package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.Descarga;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.VerificacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.siagie.CalificacionesSiagie;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioMatriculas;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * QA del sprint 10 (libretas y SIAGIE): retiros y traslados después del cierre frente a la exportación, la sección vacía,
 * el aviso único de hermanos en secciones distintas, el nombre con tildes y Ñ en el PDF real, la verificación desde otro
 * colegio y con el código escrito a mano, y la libreta del retirado antes del cierre.
 */
class QaSprint10LibretasTest extends PruebaLibretas {

	@Autowired
	private CalificacionesSiagie siagie;

	@Autowired
	private ServicioMatriculas servicioMatriculas;

	// ------------------------------------------------------------------ SIAGIE: retiros, traslados y sección vacía

	/**
	 * Hallazgo QA-3 (medio): un alumno retirado DESPUÉS del cierre con una corrección aprobada no recibe versión nueva (la
	 * emisión solo reemite a los que siguen en la sección) y antes eso bloqueaba la exportación de TODA la sección para
	 * siempre («Espere a que se emitan las libretas corregidas»). Ahora se exporta con los alumnos de hoy.
	 */
	@Test
	void debeExportarAlSiagieAunqueUnRetiradoTengaUnaCorreccionSinLibretaNueva() {
		cerrarYEmitir();
		corregir(lucas, competencias("MAT").get(0), "AD", null);
		jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = ? WHERE id = ?", LocalDate.of(2026, 5, 5),
				lucas);
		assertThat(emitir().total()).as("al retirado no se le reemite").isZero();
		assertThat(emitir().total()).isZero();

		CalificacionesSiagie.Archivo archivo = como(EscenarioCobranza.DIRECCION, () -> siagie.exportar(seccion, periodo));

		assertThat(archivo.filas()).isEqualTo(2);
	}

	/**
	 * Hallazgo QA-4 (medio): un alumno trasladado DESPUÉS del cierre tiene su libreta en la sección anterior. Antes la
	 * exportación de la sección nueva pedía «espere unos minutos» para siempre (buscaba libretas por sección); ahora usa la
	 * libreta vigente de cada matrícula, donde se haya emitido. La sección anterior se exporta sin él.
	 */
	@Test
	void debeExportarLaSeccionNuevaDeUnTrasladadoDespuesDelCierre() {
		cerrarYEmitir();
		Long quintoB = escuela.primaria5B2026();
		como(EscenarioCobranza.ADMINISTRACION, () -> servicioMatriculas.cambiarSeccion(mateo,
				new CambiarSeccionRequest(quintoB, "La familia pidió el turno de la otra sección")));
		assertThat(emitir().total()).as("su libreta del I bimestre ya existe").isZero();

		CalificacionesSiagie.Archivo deB = como(EscenarioCobranza.DIRECCION, () -> siagie.exportar(quintoB, periodo));
		CalificacionesSiagie.Archivo deA = como(EscenarioCobranza.DIRECCION, () -> siagie.exportar(seccion, periodo));

		assertThat(deB.filas()).isEqualTo(1);
		assertThat(deA.filas()).isEqualTo(2);
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'LIBRETA_HUELLA_NO_COINCIDE'"))
				.as("agrupar por sección no da huellas falsas").isZero();
	}

	/** Hallazgo QA-5 (bajo): una sección sin alumnos responde con un mensaje, no con un error del servidor. */
	@Test
	void debeResponderConUnMensajeAlExportarUnaSeccionSinAlumnos() {
		anaPoneA();
		cerrarPeriodo();
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> siagie.exportar(escuela.primaria2B2026(),
				periodo))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no tiene alumnos");
		assertThat(contar("SELECT COUNT(*) FROM exportacion_calificaciones")).isZero();
	}

	/**
	 * Hallazgo QA-6 (bajo): la pantalla dice «desactualizada» solo si cambió una libreta de los alumnos de hoy. Antes
	 * comparaba con todas las libretas de la sección (también la de un retirado) y toda exportación salía desactualizada.
	 */
	@Test
	void noDebeMarcarDesactualizadaLaExportacionPorLaLibretaDeUnRetirado() {
		cerrarYEmitir();
		jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = ? WHERE id = ?", LocalDate.of(2026, 5, 5),
				lucas);
		como(EscenarioCobranza.DIRECCION, () -> siagie.exportar(seccion, periodo));

		CalificacionesSiagie.Vista vista = como(EscenarioCobranza.DIRECCION, () -> siagie.vista());

		assertThat(vista.ultimas()).singleElement().satisfies(u -> assertThat(u.desactualizada()).isFalse());
		// Y sí lo dice cuando una libreta de un alumno de hoy cambió después de exportar.
		corregir(mateo, competencias("MAT").get(0), "AD", null);
		emitir();
		assertThat(como(EscenarioCobranza.DIRECCION, () -> siagie.vista()).ultimas()).singleElement()
				.satisfies(u -> assertThat(u.desactualizada()).isTrue());
	}

	/** Delegación solo a Administración: Caja no exporta ni con la pantalla (403) ni con el servicio. */
	@Test
	void cajaNoDebeExportarCalificacionesNiConUnaDelegacion() throws Exception {
		cerrarYEmitir();
		MockHttpServletResponse r = mvc.perform(get("/academico/siagie/calificaciones").param("seccion", seccion.toString())
				.param("periodo", periodo.toString()).with(UsuariosDePrueba.como(UsuariosDePrueba.autenticado(1L, 77L,
						"cajera.qa", "Cajera QA", false, EnumSet.of(Rol.CAJA))))).andReturn().getResponse();
		assertThat(r.getStatus()).isEqualTo(403);
		assertThat(contar("SELECT COUNT(*) FROM exportacion_calificaciones")).isZero();
	}

	// ------------------------------------------------------------------ retiros antes del cierre y avisos

	/** El retirado antes del cierre no recibe libreta y la integridad no lo cuenta como faltante. */
	@Test
	void noDebeEmitirLibretaAlRetiradoAntesDelCierreNiAlertarPorEl() {
		anaPoneA();
		jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = ? WHERE id = ?", LocalDate.of(2026, 4, 20),
				lucas);
		cerrarPeriodo();
		assertThat(emitir().alCierre()).isEqualTo(2);
		reloj.avanzar(Duration.ofHours(1));
		List<AlertaRevision> alertas = como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas());
		assertThat(alertas).noneMatch(a -> a.texto().contains("no tienen la libreta"));
		assertThat(contar("SELECT COUNT(*) FROM libreta_emitida WHERE matricula_id = ?", lucas)).isZero();
	}

	/** Hermanos en secciones distintas: un solo aviso a la familia por periodo (Rosa) y otro a la de Pedro. */
	@Test
	void debeAvisarUnaSolaVezALaFamiliaConHermanosEnSeccionesDistintas() {
		como(EscenarioCobranza.ADMINISTRACION, () -> servicioMatriculas.cambiarSeccion(valeria,
				new CambiarSeccionRequest(escuela.primaria5B2026(), "Cambio de sección pedido por la familia")));
		guardar(ana, "MAT", hoja("MAT", List.of(mateo, lucas), "A", null));
		cerrarPeriodo();
		assertThat(emitir().alCierre()).isEqualTo(3);
		assertThat(avisar()).as("Rosa (Mateo en 5.° A y Valeria en 5.° B) y Pedro").isEqualTo(2);
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE familia_id = ? AND tipo = 'LIBRETA_CORREGIDA'", familiaRosa))
				.isZero();
		assertThat(avisar()).as("una pasada repetida no duplica").isZero();
	}

	/**
	 * Feriados: una libreta en revisión detectada el lunes 27 de julio de 2026 se entrega a más tardar el viernes 31 (los 2
	 * días hábiles saltan el 28 y el 29, Fiestas Patrias), y la familia lo ve en el portal.
	 */
	@Test
	void debeContarElPlazoDeLaLibretaEnRevisionSinLosFeriados() throws Exception {
		cerrarYEmitir();
		Long id = libreta(mateo);
		jdbc.update("UPDATE calificacion SET nivel_logro = 'C' WHERE id = ?", vigente(mateo, competencias("MAT").get(0)));
		hoyEs(LocalDate.of(2026, 7, 27));
		org.springframework.test.web.servlet.MvcResult descarga = mvc.perform(get("/familia/libretas/" + id + ".pdf")
				.with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn();
		MockHttpServletResponse r = descarga.getResponse();
		assertThat(r.getStatus()).isEqualTo(303);
		assertThat(r.getRedirectedUrl()).isEqualTo("/familia/libretas");
		assertThat((String) descarga.getFlashMap().get("error")).contains("a más tardar el 31/07/2026");
	}

	// ------------------------------------------------------------------ PDF real y verificación

	/** Un alumno con tildes y Ñ: la libreta real (foto, huella y PDF) conserva su nombre exacto. */
	@Test
	void debeImprimirElNombreConTildesYEnieEnLaLibretaReal() {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long alumno = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71234599", "Ñahui", "Peña", "José Ángel",
				LocalDate.of(2015, 4, 2), "41234599", "Peña", "Quispe", "Íñigo", "987111299", "inigo.pena@correo.pe",
				seccion)).alumnoId();
		SecurityContextHolder.clearContext();
		Long matricula = matricula(alumno);
		cerrarYEmitir();
		Long id = libreta(matricula);
		assertThat(jdbc.queryForObject("SELECT alumno_nombre FROM libreta_emitida WHERE id = ?", String.class, id))
				.isEqualTo("Ñahui Peña, José Ángel");
		Descarga d = como(EscenarioCobranza.DIRECCION, () -> libretasColegio.imprimir(id));
		assertThat(texto(d.pdf())).contains("Ñahui Peña, José Ángel");
	}

	/** El código escrito a mano (minúsculas, espacios, la huella en mayúsculas) se reconoce. */
	@Test
	void debeReconocerElCodigoEscritoAManoConEspaciosYMayusculas() {
		cerrarYEmitir();
		Long id = libreta(mateo);
		String huella = jdbc.queryForObject("SELECT huella_contenido FROM libreta_emitida WHERE id = ?", String.class, id);
		String aMano = "  lib-" + id + " - " + huella.substring(0, 8).toUpperCase() + " " + huella.substring(8, 16)
				.toUpperCase() + " ";
		VerificacionVista v = como(EscenarioCobranza.DIRECCION, () -> verificacion.verificar(aMano));
		assertThat(v.existe()).isTrue();
		assertThat(v.coincide()).isTrue();
	}

	/** Multi-colegio: la Dirección de otro colegio verifica un código real del colegio 1 y no lo encuentra. */
	@Test
	void noDebeVerificarDesdeOtroColegioUnaLibretaReal() {
		cerrarYEmitir();
		Long id = libreta(mateo);
		String codigo = "LIB-" + id + "-" + jdbc.queryForObject("SELECT huella_contenido FROM libreta_emitida WHERE id = ?",
				String.class, id).substring(0, 16);
		jdbc.update("INSERT INTO colegio (nombre) VALUES ('Colegio de Prueba B')");
		long colegioB = jdbc.queryForObject("SELECT MAX(id) FROM colegio", Long.class);
		VerificacionVista v = como(UsuariosDePrueba.autenticado(colegioB, 901L, "director.b", "Director B", false,
				EnumSet.of(Rol.DIRECTOR)), () -> verificacion.verificar(codigo));
		assertThat(v.existe()).isFalse();
		assertThat(contar("SELECT COUNT(*) FROM acceso_dato_personal WHERE tipo = 'LIBRETA'")).isZero();
	}
}
