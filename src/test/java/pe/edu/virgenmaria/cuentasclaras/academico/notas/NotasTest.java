package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.HojaNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ResultadoNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.HuellaConclusion;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 2 (secciones 3.5 y 6 del brief): la hoja de notas del docente. Un guardado es UN lote con UNA firma y una
 * fila por celda que cambia; solo el docente del área (el tutor, en las transversales) pone notas; Dirección no califica; el
 * auxiliar y una cobertura tampoco; la conclusión que exige la norma; «sin nota» con el periodo abierto; después del cierre,
 * nada directo (E1); la matrícula RESERVADA recibe nota igual (C1).
 */
class NotasTest extends PruebaNotas {

	/** 30 alumnos por 4 competencias en un guardado: un lote, una firma y 120 filas a nombre del docente. */
	@Test
	void elDocenteGuardaTreintaAlumnosEnUnLoteConUnaFirma() {
		List<Long> matriculas = new ArrayList<>(List.of(mateo, valeria, lucas));
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		for (int i = 0; i < 27; i++) {
			String n = String.format("%02d", i);
			String letra = "" + (char) ('a' + i / 10) + (char) ('a' + i % 10);
			Long alumno = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("720000" + n, "Ramos", "Vega", "Alumn" + letra,
					LocalDate.of(2015, 3, 1), "420000" + n, "Ramos", "Soto", "Padr" + letra, "9870000" + n,
					"padre" + n + "@correo.pe", seccion)).alumnoId();
			matriculas.add(matricula(alumno));
		}
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		assertThat(competencias("MAT")).hasSize(4);

		ResultadoNotas resultado = guardar(ana, "MAT", hoja("MAT", matriculas, "A", null));

		assertThat(resultado.nuevas()).isEqualTo(120);
		assertThat(contar("SELECT COUNT(*) FROM lote_calificacion")).isEqualTo(1);
		assertThat(contar("SELECT filas FROM lote_calificacion")).isEqualTo(120);
		assertThat(contar("SELECT COUNT(*) FROM calificacion WHERE creado_por = 'ana.notas' AND origen = 'REGISTRO'"))
				.isEqualTo(120);
		assertThat(contar("SELECT COUNT(*) FROM firma_operacion WHERE clave LIKE 'lote_calificacion:%'")).isEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'NOTAS_GUARDADAS'")).isEqualTo(1);
	}

	/** Solo cambia lo que cambió: el segundo guardado inserta solo las celdas distintas (CAMBIO_ABIERTO). */
	@Test
	void unCambioReemplazaSoloLaCeldaQueCambia() {
		anaPoneA();
		List<Long> mat = competencias("MAT");
		NotasRequest cambio = new NotasRequest(List.of(new NotaPedida(mateo, mat.get(0), "AD", null),
				new NotaPedida(valeria, mat.get(0), "A", null)), null);
		ResultadoNotas r = guardar(ana, "MAT", cambio);
		assertThat(r.cambios()).isEqualTo(1);
		assertThat(r.secuencia()).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT nivel_logro FROM calificacion WHERE id = ?", String.class,
				vigente(mateo, mat.get(0)))).isEqualTo("AD");
		assertThatThrownBy(() -> guardar(ana, "MAT", cambio)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No hay notas nuevas ni cambios");
	}

	/** E2: un área que no es suya en su misma sección responde 404 (no «prohibido»: no existe para él). */
	@Test
	void areaAjenaResponde404() {
		assertThatThrownBy(() -> guardar(luis, "MAT", hoja("MAT", List.of(mateo), "A", null)))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(luis), () -> registro.hoja(seccion, area("MAT"), null,
				null))).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(contar("SELECT COUNT(*) FROM calificacion")).isZero();
	}

	/** Decisión 173: Dirección ve, pero no pone notas. */
	@Test
	void direccionNoCalifica() {
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> registro.guardar(seccion, area("MAT"), periodo,
				hoja("MAT", List.of(mateo), "A", null)))).isInstanceOf(AccessDeniedException.class);
	}

	/** E25: ni el auxiliar ni una cobertura califican (no tienen áreas). */
	@Test
	void auxiliarYCoberturaNoCalifican() {
		assertThatThrownBy(() -> guardar(rita, "MAT", hoja("MAT", List.of(mateo), "A", null)))
				.isInstanceOf(RecursoNoEncontradoException.class);
		Usuario cobertura = docente("cobertura.notas");
		hoyEs(LocalDate.of(2026, 3, 3));
		Long id = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(
				new pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest(seccion, cobertura.getId(),
						TipoAsignacion.DOCENTE, null, MOTIVO, LocalDate.of(2026, 3, 5))));
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja,
				jdbc, "asignacion_docente", id);
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> guardar(cobertura, "MAT", hoja("MAT", List.of(mateo), "A", null)))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	/** Desviación 8 de la tanda 1: el tutor califica las competencias transversales de su nivel; otro docente, no. */
	@Test
	void elTutorCalificaLasTransversales() {
		ResultadoNotas r = guardar(tito, "TRV", hoja("TRV", List.of(mateo, valeria), "A", null));
		assertThat(r.nuevas()).isEqualTo(2 * competencias("TRV").size());
		assertThatThrownBy(() -> guardar(ana, "TRV", hoja("TRV", List.of(mateo), "A", null)))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	/** E18: en Primaria, B y C exigen la conclusión; A no. Con conclusion_siempre, todo nivel la exige. */
	@Test
	void conclusionExigidaPorElNivelYPorElColegio() {
		assertThatThrownBy(() -> guardar(ana, "MAT", hoja("MAT", List.of(mateo), "B", null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("exige la conclusión descriptiva para B");
		assertThatThrownBy(() -> guardar(ana, "MAT", hoja("MAT", List.of(mateo), "B", "corta")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("va de 10 a 500");
		String texto = "Resuelve problemas con apoyo; se recomienda practicar fracciones con material concreto.";
		guardar(ana, "MAT", hoja("MAT", List.of(mateo), "B", texto));
		Long una = vigente(mateo, competencias("MAT").get(0));
		assertThat(jdbc.queryForObject("SELECT conclusion_sha256 FROM calificacion WHERE id = ?", String.class, una))
				.isEqualTo(HuellaConclusion.de(texto));
		guardar(ana, "MAT", hoja("MAT", List.of(valeria), "A", null));

		como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(), valores(true)));
		assertThatThrownBy(() -> guardar(ana, "MAT", hoja("MAT", List.of(lucas), "A", null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("exige la conclusión descriptiva para A");
		assertThat(como(EscenarioCobranza.DIRECCION, () -> catalogo.exigeConclusion(version(), Nivel.INICIAL, "A"))).isTrue();
		assertThat(como(EscenarioCobranza.DIRECCION, () -> catalogo.exigeConclusion(version(), Nivel.SECUNDARIA, "B")))
				.isFalse();
		assertThat(como(EscenarioCobranza.DIRECCION, () -> catalogo.exigeConclusion(version(), Nivel.SECUNDARIA, "C")))
				.isTrue();
	}

	/** Con el periodo abierto, «sin nota en este periodo» quita la nota con otra fila (nivel NULL), nunca borrando. */
	@Test
	void sinNotaEnElPeriodo() {
		anaPoneA();
		Long competencia = competencias("MAT").get(0);
		Long antes = vigente(mateo, competencia);
		ResultadoNotas r = guardar(ana, "MAT", new NotasRequest(List.of(new NotaPedida(mateo, competencia, null, null)),
				null));
		assertThat(r.sinNota()).isEqualTo(1);
		Long ahora = vigente(mateo, competencia);
		assertThat(jdbc.queryForMap("SELECT nivel_logro, origen, reemplaza_id FROM calificacion WHERE id = ?", ahora))
				.containsEntry("NIVEL_LOGRO", null).containsEntry("ORIGEN", "CAMBIO_ABIERTO").containsEntry("REEMPLAZA_ID", antes);
		assertThat(contar("SELECT COUNT(*) FROM calificacion WHERE id = ?", antes)).isEqualTo(1);
		HojaNotas hoja = como(UsuariosDePrueba.autenticado(ana), () -> registro.hoja(seccion, area("MAT"), periodo, null));
		assertThat(hoja.sinNota()).isEqualTo(1);
	}

	/** E1: después del cierre, la hoja es de solo lectura y un guardado directo se rechaza (va por solicitud). */
	@Test
	void despuesDelCierreVaPorSolicitud() {
		anaPoneA();
		cerrarPeriodo();
		HojaNotas hoja = como(UsuariosDePrueba.autenticado(ana), () -> registro.hoja(seccion, area("MAT"), periodo, null));
		assertThat(hoja.editable()).isFalse();
		assertThat(hoja.aviso()).contains("está cerrado");
		assertThatThrownBy(() -> guardar(ana, "MAT", hoja("MAT", List.of(mateo), "AD", null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("va por solicitud");
	}

	/** C1 (crítico): la matrícula RESERVADA (impaga) recibe nota igual que las demás; ningún filtro mira la deuda. */
	@Test
	void matriculaReservadaRecibeNota() {
		jdbc.update("UPDATE matricula SET estado = 'RESERVADA' WHERE id = ?", lucas);
		HojaNotas hoja = como(UsuariosDePrueba.autenticado(ana), () -> registro.hoja(seccion, area("MAT"), periodo, null));
		assertThat(hoja.filas()).extracting(HojaNotas.FilaAlumno::matriculaId).contains(lucas);
		ResultadoNotas r = guardar(ana, "MAT", hoja("MAT", List.of(lucas), "A", null));
		assertThat(r.nuevas()).isEqualTo(4);
	}

	/** Sección 4 del brief: el docente del sprint 9 sin áreas del catálogo ve el aviso y no tiene con qué guardar. */
	@Test
	void sinAreasDelCatalogoVeElAviso() {
		Usuario delSprint9 = docente("pepe.sprint9");
		Long id = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(
				new pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest(escuela.primaria5B2026(),
						delSprint9.getId(), TipoAsignacion.DOCENTE, "Matemática", MOTIVO)));
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja,
				jdbc, "asignacion_docente", id);
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		HojaNotas hoja = como(UsuariosDePrueba.autenticado(delSprint9), () -> registro.hoja(escuela.primaria5B2026(),
				area("MAT"), null, null));
		assertThat(hoja.editable()).isFalse();
		assertThat(hoja.aviso()).startsWith("Tu asignación en ").endsWith("todavía no tiene áreas del catálogo. Pide a "
				+ "Dirección que la complete.");
		assertThat(como(UsuariosDePrueba.autenticado(delSprint9), () -> registro.mias()).sinAreas()).hasSize(1);
	}
}
