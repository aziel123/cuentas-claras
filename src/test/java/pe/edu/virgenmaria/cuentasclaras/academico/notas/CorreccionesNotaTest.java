package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ResultadoNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.HuellaConclusion;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 2 (sección 3.6; E4, E5, E6, E23 y M8): con el periodo CERRADO, una nota cambia solo por una solicitud que
 * aprueba OTRA persona de Dirección o Promotoría, que no la pidió ni registró la nota; entra exactamente lo aprobado (el
 * nivel y el texto de su borrador); el texto nunca va en la solicitud ni en la bitácora. La nota tardía: 48 horas, solo
 * competencias sin nota.
 */
class CorreccionesNotaTest extends PruebaNotas {

	private static final String CONCLUSION = "Resuelve problemas de cantidad con apoyo; se recomienda reforzar fracciones.";

	private Long pedirComoAna(Long calificacion, String nivel, String conclusion) {
		return como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedir(calificacion, nivel, conclusion,
				MOTIVO_CORRECCION));
	}

	private void aprueba(pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado quien, String entidad,
			Long id) {
		try {
			EscenarioAprobaciones.aprueba(quien, bandeja, jdbc, entidad, id);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/** Con el periodo abierto no hay corrección: se cambia en la hoja. */
	@Test
	void conElPeriodoAbiertoNoSePideCorreccion() {
		anaPoneA();
		Long nota = vigente(mateo, competencias("MAT").get(0));
		assertThatThrownBy(() -> pedirComoAna(nota, "AD", null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("está abierto");
	}

	/** E4: quien pide no aprueba (Dirección pide; la misma persona no la aprueba; otra sí). */
	@Test
	void quienPideNoAprueba() {
		anaPoneA();
		cerrarPeriodo();
		Long nota = vigente(mateo, competencias("MAT").get(0));
		como(EscenarioCobranza.DIRECCION, () -> correcciones.pedir(nota, "AD", null, MOTIVO_CORRECCION));
		assertThatThrownBy(() -> aprueba(EscenarioCobranza.DIRECCION, "calificacion", nota))
				.isInstanceOf(AutoaprobacionSolicitudException.class);
		aprueba(EscenarioCobranza.PROMOTORIA, "calificacion", nota);
		Long nueva = vigente(mateo, competencias("MAT").get(0));
		assertThat(jdbc.queryForMap("SELECT nivel_logro, origen, creado_por, reemplaza_id FROM calificacion WHERE id = ?",
				nueva)).containsEntry("NIVEL_LOGRO", "AD").containsEntry("ORIGEN", "CORRECCION_APROBADA")
				.containsEntry("CREADO_POR", "promotor").containsEntry("REEMPLAZA_ID", nota);
	}

	/** E5: un director que también enseña registró la nota: no aprueba su corrección aunque la haya pedido otra persona. */
	@Test
	void elAutorDeLaNotaNoApruebaSuCorreccion() {
		Usuario dirDocente = docente("dir.docente", Rol.DIRECTOR);
		asignar(dirDocente, TipoAsignacion.DOCENTE, List.of(area("AYC")));
		guardar(dirDocente, "AYC", hoja("AYC", List.of(mateo), "A", null));
		cerrarPeriodo();
		Long nota = vigente(mateo, competencias("AYC").get(0));
		como(EscenarioCobranza.DIRECCION, () -> correcciones.pedir(nota, "AD", null, MOTIVO_CORRECCION));
		assertThatThrownBy(() -> aprueba(UsuariosDePrueba.autenticado(dirDocente), "calificacion", nota))
				.isInstanceOf(AutoaprobacionSolicitudException.class);
		aprueba(EscenarioCobranza.PROMOTORIA, "calificacion", nota);
		assertThat(jdbc.queryForObject("SELECT nivel_logro FROM calificacion WHERE id = ?", String.class,
				vigente(mateo, competencias("AYC").get(0)))).isEqualTo("AD");
	}

	/**
	 * E6 y M8: lo aprobado es lo que entra (el nivel y el texto del borrador, con su huella). El texto de la conclusión no
	 * está en la solicitud ni en la bitácora: solo su SHA-256.
	 */
	@Test
	void loAprobadoEsLoQueEntraYElTextoQuedaFueraDeLaSolicitudYLaBitacora() {
		anaPoneA();
		cerrarPeriodo();
		Long nota = vigente(mateo, competencias("MAT").get(0));
		Long solicitud = pedirComoAna(nota, "B", CONCLUSION);
		String datos = jdbc.queryForObject("SELECT datos FROM solicitud_cambio WHERE id = ?", String.class, solicitud);
		assertThat(datos).contains("\"nivel\":\"B\"", "\"borrador_id\":", "\"conclusion_sha256\":\""
				+ HuellaConclusion.de(CONCLUSION) + "\"").doesNotContain("fracciones");
		assertThat(jdbc.queryForObject("SELECT texto FROM borrador_conclusion", String.class)).isEqualTo(CONCLUSION);

		aprueba(EscenarioCobranza.PROMOTORIA, "calificacion", nota);
		Map<String, Object> nueva = jdbc.queryForMap("SELECT nivel_logro, conclusion, conclusion_sha256, solicitud_id FROM "
				+ "calificacion WHERE id = ?", vigente(mateo, competencias("MAT").get(0)));
		assertThat(nueva).containsEntry("NIVEL_LOGRO", "B").containsEntry("CONCLUSION", CONCLUSION)
				.containsEntry("CONCLUSION_SHA256", HuellaConclusion.de(CONCLUSION)).containsEntry("SOLICITUD_ID", solicitud);
		List<String> bitacora = jdbc.queryForList("SELECT CONCAT(COALESCE(detalle, ''), '|', COALESCE(valor_anterior, ''), "
				+ "'|', COALESCE(valor_nuevo, '')) FROM evento_auditoria", String.class);
		assertThat(bitacora).isNotEmpty().noneMatch(l -> l.contains("fracciones"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CALIFICACION_CORREGIDA'",
				Long.class)).isEqualTo(1);
	}

	/** Una corrección se pide sobre la vigente, con motivo, y cambia algo. */
	@Test
	void laCorreccionCambiaAlgoYLlevaMotivo() {
		anaPoneA();
		cerrarPeriodo();
		Long nota = vigente(mateo, competencias("MAT").get(0));
		assertThatThrownBy(() -> pedirComoAna(nota, "A", null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("cambia el nivel o la conclusión");
		assertThatThrownBy(() -> pedirComoAna(nota, "B", null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("exige la conclusión");
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedir(nota, "AD", null,
				"corto"))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("20 caracteres");
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(luis), () -> correcciones.pedir(nota, "AD", null,
				MOTIVO_CORRECCION))).as("otra área de la misma sección").isInstanceOf(RecursoNoEncontradoException.class);
	}

	/** E23: la nota tardía es solo para competencias sin nota, la aprueba otra persona y vale 48 horas, una vez. */
	@Test
	void notaTardiaEnCuarentaYOchoHorasSoloSinNota() {
		anaPoneA();
		cerrarPeriodo();
		assertThatThrownBy(() -> como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedirTardia(seccion, area("MAT"),
				periodo, MOTIVO_CORRECCION))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("tienen nota");
		Long solicitud = como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedirTardia(seccion, area("CYT"),
				periodo, MOTIVO_CORRECCION));
		Long asignacion = jdbc.queryForObject("SELECT entidad_id FROM solicitud_cambio WHERE id = ?", Long.class, solicitud);
		NotasRequest sinAprobar = new NotasRequest(hoja("CYT", List.of(mateo), "A", null).notas(), solicitud);
		assertThatThrownBy(() -> guardar(ana, "CYT", sinAprobar)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("todavía no está aprobada");
		aprueba(EscenarioCobranza.PROMOTORIA, "asignacion_docente", asignacion);

		ResultadoNotas r = guardar(ana, "CYT", new NotasRequest(hoja("CYT", List.of(mateo, valeria), "A", null).notas(),
				solicitud));
		assertThat(r.nuevas()).isEqualTo(2 * competencias("CYT").size());
		assertThat(contar("SELECT COUNT(*) FROM calificacion WHERE origen = 'REGISTRO_TARDIO'")).isEqualTo(r.nuevas());
		assertThatThrownBy(() -> guardar(ana, "CYT", new NotasRequest(hoja("CYT", List.of(lucas), "A", null).notas(),
				solicitud))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya se registró");
	}

	@Test
	void laNotaTardiaVenceALasCuarentaYOchoHoras() {
		anaPoneA();
		cerrarPeriodo();
		Long solicitud = como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedirTardia(seccion, area("CYT"),
				periodo, MOTIVO_CORRECCION));
		Long asignacion = jdbc.queryForObject("SELECT entidad_id FROM solicitud_cambio WHERE id = ?", Long.class, solicitud);
		aprueba(EscenarioCobranza.PROMOTORIA, "asignacion_docente", asignacion);
		reloj.avanzar(Duration.ofHours(49));
		assertThatThrownBy(() -> guardar(ana, "CYT", new NotasRequest(hoja("CYT", List.of(mateo), "A", null).notas(),
				solicitud))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("venció");
		assertThatThrownBy(() -> guardar(ana, "CYT", new NotasRequest(List.of(new NotaPedida(mateo,
				competencias("MAT").get(0), "AD", null)), solicitud))).isInstanceOf(RuntimeException.class);
	}
}
