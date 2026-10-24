package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.AvanceVista;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.HojaNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ResultadoNotas;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioMatriculas;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 10 (notas): los huecos que no cubrían las pruebas de la tanda 2. Traslados y retiros a mitad de periodo,
 * los límites de la conclusión, la escala completa de la RVM 048-2024, el borde de las 48 horas, el cierre en hora de Lima
 * frente a UTC y la segregación cuando quien registró la nota también es de Dirección.
 */
class QaSprint10NotasTest extends PruebaNotas {

	@Autowired
	private ServicioMatriculas servicioMatriculas;

	// ------------------------------------------------------------------ traslados y retiros a mitad de periodo

	/**
	 * Hallazgo QA-1 (alto): un alumno trasladado a mitad de periodo conserva la nota que puso su docente anterior, la hoja
	 * de la sección nueva la muestra y su docente la puede cambiar. Antes la hoja buscaba las notas por sección: el alumno
	 * salía «sin nota» y al ponerle una, el guardado de TODA la hoja fallaba con uk_calificacion_inicial (un 500).
	 */
	@Test
	void debeConservarLaNotaDelTrasladadoYDejarQueSuNuevaSeccionLaCambie() {
		anaPoneA();
		Long quintoB = escuela.primaria5B2026();
		Usuario eva = docente("eva.quintob");
		Long propuesta = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(ProponerAsignacionRequest
				.conAreas(quintoB, eva.getId(), TipoAsignacion.DOCENTE, MOTIVO, List.of(area("MAT")))));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", propuesta);
		SecurityContextHolder.clearContext();
		como(EscenarioCobranza.ADMINISTRACION, () -> servicioMatriculas.cambiarSeccion(mateo,
				new CambiarSeccionRequest(quintoB, "La familia pidió el turno de la otra sección")));

		HojaNotas hoja = como(UsuariosDePrueba.autenticado(eva), () -> registro.hoja(quintoB, area("MAT"), periodo, null));
		HojaNotas.FilaAlumno filaMateo = hoja.filas().stream().filter(f -> f.matriculaId().equals(mateo)).findFirst()
				.orElseThrow();
		assertThat(filaMateo.celdas()).as("la nota de su sección anterior sigue siendo la vigente")
				.allSatisfy(c -> assertThat(c.nivel()).isEqualTo("A"));
		assertThat(hoja.sinNota()).isZero();

		Long competencia = competencias("MAT").get(0);
		Long anterior = vigente(mateo, competencia);
		ResultadoNotas r = como(UsuariosDePrueba.autenticado(eva), () -> registro.guardar(quintoB, area("MAT"), periodo,
				new NotasRequest(List.of(new NotaPedida(mateo, competencia, "AD", null)), null)));

		assertThat(r.cambios()).isEqualTo(1);
		assertThat(r.nuevas()).isZero();
		Long nueva = vigente(mateo, competencia);
		assertThat(jdbc.queryForMap("SELECT origen, seccion_id, reemplaza_id, nivel_logro, creado_por FROM calificacion "
				+ "WHERE id = ?", nueva)).containsEntry("ORIGEN", "CAMBIO_ABIERTO").containsEntry("SECCION_ID", quintoB)
				.containsEntry("REEMPLAZA_ID", anterior).containsEntry("NIVEL_LOGRO", "AD")
				.containsEntry("CREADO_POR", "eva.quintob");
		// En 5.° A ya no figura Mateo.
		HojaNotas deAna = como(UsuariosDePrueba.autenticado(ana), () -> registro.hoja(seccion, area("MAT"), periodo, null));
		assertThat(deAna.filas()).extracting(HojaNotas.FilaAlumno::matriculaId).doesNotContain(mateo);
	}

	/**
	 * Hallazgo QA-2 (medio): el avance y el resumen de cierre cuentan solo las notas de quienes reciben nota hoy. Antes la
	 * nota de un retirado contaba (y el tope «esperadas» la escondía): con un alumno vigente sin nota, el avance decía 100 %.
	 */
	@Test
	void debeContarComoPendienteAlVigenteSinNotaAunqueUnRetiradoTengaNota() {
		guardar(ana, "MAT", hoja("MAT", List.of(mateo, valeria), "A", null));
		jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = ? WHERE id = ?", LocalDate.of(2026, 3, 20),
				mateo);

		AvanceVista vista = como(EscenarioCobranza.DIRECCION, () -> avance.avance(periodo));
		AvanceVista.FilaAvance matematica = vista.filas().stream().filter(f -> f.seccionId().equals(seccion)
				&& f.areaId().equals(area("MAT"))).findFirst().orElseThrow();
		assertThat(matematica.esperadas()).isEqualTo(2 * 4);
		assertThat(matematica.conNota()).as("solo las 4 de Valeria; Lucas no tiene").isEqualTo(4);
		assertThat(matematica.faltan()).isEqualTo(4);
	}

	/** QA-2: la nota tardía no se rechaza con «todas tienen nota» porque un retirado tenga las suyas. */
	@Test
	void debePermitirLaNotaTardiaAunqueUnRetiradoTengaNotas() {
		guardar(ana, "MAT", hoja("MAT", List.of(mateo, valeria), "A", null));
		jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = ? WHERE id = ?", LocalDate.of(2026, 3, 20),
				mateo);
		cerrarPeriodo();

		Long solicitud = como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedirTardia(seccion, area("MAT"),
				periodo, MOTIVO_CORRECCION));

		assertThat(solicitud).isNotNull();
		assertThat(jdbc.queryForObject("SELECT resumen FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.contains("4 sin nota");
	}

	/** QA-2: un exonerado con notas de antes de su exoneración (mismo periodo) tampoco infla el avance. */
	@Test
	void debeDescontarLasNotasDelExoneradoDelAvance() {
		guardar(luis, "COM", hoja("COM", List.of(mateo), "A", null));
		Long religion = area("REL");
		Usuario rey = docente("rey.religion");
		asignar(rey, TipoAsignacion.DOCENTE, List.of(religion));
		guardar(rey, "REL", hoja("REL", List.of(mateo), "A", null));
		como(EscenarioCobranza.DIRECCION, () -> exoneraciones.exonerar(new pe.edu.virgenmaria.cuentasclaras.academico
				.notas.dto.ExonerarRequest(mateo, religion, "EXP-2026-014", "Escrito de la familia por su credo")));

		AvanceVista vista = como(EscenarioCobranza.DIRECCION, () -> avance.avance(periodo));
		AvanceVista.FilaAvance rel = vista.filas().stream().filter(f -> f.seccionId().equals(seccion)
				&& f.areaId().equals(religion)).findFirst().orElseThrow();
		int competenciasRel = competencias("REL").size();
		assertThat(rel.esperadas()).isEqualTo(2 * competenciasRel);
		assertThat(rel.conNota()).as("las de Mateo (exonerado) no cuentan").isZero();
	}

	// ------------------------------------------------------------------ escalas y conclusión

	/** La escala del catálogo (RVM 048-2024): Inicial A, B y C; Primaria B y C; Secundaria C. Los 12 valores. */
	@ParameterizedTest(name = "{0} {1} exige conclusión: {2}")
	@CsvSource({ "INICIAL,AD,false", "INICIAL,A,true", "INICIAL,B,true", "INICIAL,C,true", "PRIMARIA,AD,false",
			"PRIMARIA,A,false", "PRIMARIA,B,true", "PRIMARIA,C,true", "SECUNDARIA,AD,false", "SECUNDARIA,A,false",
			"SECUNDARIA,B,false", "SECUNDARIA,C,true" })
	void debeExigirLaConclusionSegunNivelYValor(Nivel nivel, String valor, boolean exige) {
		assertThat(como(EscenarioCobranza.DIRECCION, () -> catalogo.exigeConclusion(version(), nivel, valor)))
				.isEqualTo(exige);
	}

	/** Los bordes del largo: 10 y 500 (el máximo del colegio) pasan; 9 y 501 no; espacios no cuentan como conclusión. */
	@Test
	void debeRespetarLosLimitesDeLargoDeLaConclusion() {
		Long c = competencias("MAT").get(0);
		assertThatThrownBy(() -> guardar(ana, "MAT", una(mateo, c, "B", "123456789"))).isInstanceOf(
				ReglaNegocioException.class).hasMessageContaining("va de 10 a 500");
		assertThatThrownBy(() -> guardar(ana, "MAT", una(mateo, c, "B", "x".repeat(501))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("va de 10 a 500");
		assertThatThrownBy(() -> guardar(ana, "MAT", una(mateo, c, "B", "          ")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("exige la conclusión descriptiva para B");
		assertThat(guardar(ana, "MAT", una(mateo, c, "B", "1234567890")).nuevas()).isEqualTo(1);
		assertThat(guardar(ana, "MAT", una(valeria, c, "B", "y".repeat(500))).nuevas()).isEqualTo(1);
		// Los espacios de los extremos no cuentan: «   corta   » sigue siendo corta.
		assertThatThrownBy(() -> guardar(ana, "MAT", una(lucas, c, "C", "    corta     "))).isInstanceOf(
				ReglaNegocioException.class).hasMessageContaining("va de 10 a 500");
	}

	/** La huella de una conclusión con tildes y «ñ» es el SHA-256 de su UTF-8 (la base calcula el mismo con SHA2). */
	@Test
	void debeCalcularLaHuellaDeLaConclusionConTildesEnUtf8() throws Exception {
		String texto = "Señala con precisión qué logró; Ñandú y pingüino: practica la división con apoyo";
		Long c = competencias("MAT").get(0);
		guardar(ana, "MAT", una(mateo, c, "B", texto));
		String esperada = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(texto.getBytes(StandardCharsets.UTF_8)));
		assertThat(jdbc.queryForObject("SELECT conclusion_sha256 FROM calificacion WHERE id = ?", String.class,
				vigente(mateo, c))).isEqualTo(esperada);
		assertThat(jdbc.queryForObject("SELECT conclusion FROM calificacion WHERE id = ?", String.class, vigente(mateo, c)))
				.isEqualTo(texto);
	}

	// ------------------------------------------------------------------ periodos, 48 horas y zona horaria

	/** El borde de las 48 horas: a las 47 h 59 min la nota tardía todavía entra. */
	@Test
	void debeAceptarLaNotaTardiaAntesDeCumplirseLasCuarentaYOchoHoras() {
		anaPoneA();
		cerrarPeriodo();
		Long solicitud = como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedirTardia(seccion, area("CYT"),
				periodo, MOTIVO_CORRECCION));
		Long asignacion = jdbc.queryForObject("SELECT entidad_id FROM solicitud_cambio WHERE id = ?", Long.class, solicitud);
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "asignacion_docente", asignacion);
		SecurityContextHolder.clearContext();
		reloj.avanzar(Duration.ofHours(47).plusMinutes(59));
		ResultadoNotas r = guardar(ana, "CYT", new NotasRequest(hoja("CYT", List.of(mateo), "A", null).notas(), solicitud));
		assertThat(r.nuevas()).isEqualTo(competencias("CYT").size());
	}

	/**
	 * El cierre se decide con la fecha de Lima, no con la de UTC: el 30 de abril a las 23:30 de Lima ya es 1 de mayo en UTC
	 * y todavía no se puede cerrar (fin 8 de mayo − 7 días = 1 de mayo); el 1 de mayo a las 00:05 de Lima, sí.
	 */
	@Test
	void debeDecidirElCierreConLaFechaDeLimaYNoConLaDeUtc() {
		ahoraEnLima(LocalDate.of(2026, 4, 30), LocalTime.of(23, 30));
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> avance.cerrar(periodo)))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(jdbc.queryForObject("SELECT estado FROM periodo_evaluacion WHERE id = ?", String.class, periodo))
				.isEqualTo("ABIERTO");
		ahoraEnLima(LocalDate.of(2026, 5, 1), LocalTime.of(0, 5));
		como(EscenarioCobranza.DIRECCION, () -> avance.cerrar(periodo));
		assertThat(jdbc.queryForObject("SELECT estado FROM periodo_evaluacion WHERE id = ?", String.class, periodo))
				.isEqualTo("CERRADO");
	}

	/** Un periodo programado que todavía no abre no recibe notas (ni abierto antes de tiempo, antes de su inicio). */
	@Test
	void debeRechazarNotasDeUnPeriodoQueTodaviaNoAbre() {
		hoyEs(LocalDate.of(2026, 5, 4));
		como(EscenarioCobranza.DIRECCION, () -> avance.cerrar(periodo));
		Long segundo = como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(),
				LocalDate.of(2026, 5, 18), LocalDate.of(2026, 7, 24)));
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(ana), () -> registro.guardar(seccion, area("MAT"),
				segundo, hoja("MAT", List.of(mateo), "A", null)))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("todavía no abre");
		assertThat(contar("SELECT COUNT(*) FROM calificacion WHERE periodo_id = ?", segundo)).isZero();
	}

	// ------------------------------------------------------------------ segregación de funciones

	/**
	 * Quien aprobó una corrección queda como autor de la nota vigente: no aprueba la corrección siguiente de esa misma nota
	 * (E5 encadenado); la aprueba otra persona de Dirección. Además la bandeja exige que el aprobador no sea quien pidió.
	 */
	@Test
	void debeRechazarQueQuienAproboLaCorreccionAnteriorApruebeLaSiguiente() {
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, EscenarioCobranza.PROMOTORIA.getUsername(), UsuariosDePrueba.CLAVE,
				false, Rol.PROMOTOR);
		anaPoneA();
		cerrarPeriodo();
		hoyEs(LocalDate.of(2026, 5, 5));
		Long competencia = competencias("MAT").get(0);
		Long original = vigente(mateo, competencia);
		como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedir(original, "AD", null, MOTIVO_CORRECCION));
		aprobar(EscenarioCobranza.PROMOTORIA, original);
		Long corregida = vigente(mateo, competencia);
		assertThat(jdbc.queryForObject("SELECT creado_por FROM calificacion WHERE id = ?", String.class, corregida))
				.isEqualTo(EscenarioCobranza.PROMOTORIA.getUsername());

		como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedir(corregida, "A", null, "El AD se registró en la "
				+ "competencia equivocada del alumno"));
		assertThatThrownBy(() -> aprobar(EscenarioCobranza.PROMOTORIA, corregida)).isInstanceOf(RuntimeException.class);
		assertThat(vigente(mateo, competencia)).isEqualTo(corregida);
		aprobar(EscenarioCobranza.DIRECCION, corregida);
		Long tercera = vigente(mateo, competencia);
		assertThat(jdbc.queryForMap("SELECT nivel_logro, creado_por, reemplaza_id FROM calificacion WHERE id = ?", tercera))
				.containsEntry("NIVEL_LOGRO", "A").containsEntry("CREADO_POR", EscenarioCobranza.DIRECCION.getUsername())
				.containsEntry("REEMPLAZA_ID", corregida);
	}

	private void aprobar(pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado quien, Long nota) {
		try {
			EscenarioAprobaciones.aprueba(quien, bandeja, jdbc, "calificacion", nota);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private NotasRequest una(Long matricula, Long competencia, String nivel, String conclusion) {
		return new NotasRequest(List.of(new NotaPedida(matricula, competencia, nivel, conclusion)), null);
	}

	private void ahoraEnLima(LocalDate dia, LocalTime hora) {
		reloj.fijar(LocalDateTime.of(dia, hora).atZone(ZoneId.of("America/Lima")).toInstant());
	}
}
