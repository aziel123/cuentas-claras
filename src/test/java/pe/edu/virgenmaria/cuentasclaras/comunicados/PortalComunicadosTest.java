package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Sprint 9, tanda 3 (sección 3.10, E23 a E25, E28, B8): el portal de comunicados y la descarga del PDF. De otra familia
 * (aunque sea del mismo colegio): 404. Confirmar exige la sesión y el CSRF (no hay enlace que confirme). Uno retirado
 * muestra la constancia. El PDF sale con sus cabeceras y solo de un comunicado ENVIADO (salvo para quien lo aprueba).
 */
class PortalComunicadosTest extends PruebaComunicados {

	@Autowired
	MockMvc mvc;

	@Test
	void otraFamiliaResponde404() throws Exception {
		Long aB = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionB,
				"Reunión de 5.° B"), null));
		assertThat(mvc.perform(get("/familia/comunicados/" + aB).with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn()
				.getResponse().getStatus()).isEqualTo(404);
		assertThat(mvc.perform(post("/familia/comunicados/" + aB + "/confirmar").with(csrf())
				.with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(contar("SELECT COUNT(*) FROM confirmacion_lectura")).isZero();
		assertThat(mvc.perform(get("/familia/comunicados/" + aB).with(UsuariosDePrueba.como(carmenEnLinea()))).andReturn()
				.getResponse().getStatus()).isEqualTo(200);
		String lista = mvc.perform(get("/familia/comunicados").with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn()
				.getResponse().getContentAsString();
		assertThat(lista).doesNotContain("Reunión de 5.° B");
	}

	@Test
	void confirmarExigeSesionYCsrf() throws Exception {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionA,
				"Reunión de 5.° A"), null));
		assertThat(mvc.perform(post("/familia/comunicados/" + id + "/confirmar").with(UsuariosDePrueba.como(rosaEnLinea())))
				.andReturn().getResponse().getStatus()).as("sin CSRF").isEqualTo(403);
		assertThat(mvc.perform(post("/familia/comunicados/" + id + "/confirmar").with(csrf())).andReturn().getResponse()
				.getStatus()).as("sin sesión").isIn(302, 401, 403);
		assertThat(contar("SELECT COUNT(*) FROM confirmacion_lectura")).isZero();
		MvcResult ok = mvc.perform(post("/familia/comunicados/" + id + "/confirmar").with(csrf())
				.with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn();
		assertThat(ok.getResponse().getStatus()).isEqualTo(302);
		assertThat(contar("SELECT COUNT(*) FROM confirmacion_lectura WHERE comunicado_id = ? AND familia_id = ? AND "
				+ "apoderado_id = ? AND sesion_id IS NOT NULL", id, familiaRosa, rosa)).isEqualTo(1);
		// Una por familia: la segunda no suma.
		mvc.perform(post("/familia/comunicados/" + id + "/confirmar").with(csrf()).with(UsuariosDePrueba.como(rosaEnLinea())));
		assertThat(contar("SELECT COUNT(*) FROM confirmacion_lectura")).isEqualTo(1);
		// Abrir lo marca «abierto» la primera vez.
		mvc.perform(get("/familia/comunicados/" + id).with(UsuariosDePrueba.como(rosaEnLinea())));
		assertThat(contar("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ? AND familia_id = ? AND "
				+ "abierto_en IS NOT NULL", id, familiaRosa)).isEqualTo(1);
	}

	@Test
	void retiradoMuestraLaConstancia() throws Exception {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionA,
				"Reunión de 5.° A"), null));
		como(EscenarioCobranza.DIRECCION, () -> {
			envio.retirar(id, "Tenía la fecha equivocada");
			return null;
		});
		String pagina = mvc.perform(get("/familia/comunicados/" + id).with(UsuariosDePrueba.como(rosaEnLinea())))
				.andReturn().getResponse().getContentAsString();
		assertThat(pagina).contains("fue retirado").contains("Tenía la fecha equivocada").doesNotContain("patio principal");
		assertThat(mvc.perform(post("/familia/comunicados/" + id + "/confirmar").with(csrf())
				.with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn().getFlashMap().get("error")).isNotNull();
	}

	@Test
	void cabecerasDeDescarga() throws Exception {
		Long id = conPdfAprobado(aLaSeccion(EmisorRol.DIRECCION, seccionA, "Programa del Día del Logro"));
		for (var pedido : new org.springframework.test.web.servlet.RequestBuilder[] {
				get("/comunicados/" + id + "/adjunto").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)),
				get("/familia/comunicados/" + id + "/adjunto").with(UsuariosDePrueba.como(rosaEnLinea())) }) {
			var respuesta = mvc.perform(pedido).andReturn().getResponse();
			assertThat(respuesta.getStatus()).isEqualTo(200);
			assertThat(respuesta.getContentType()).isEqualTo("application/pdf");
			assertThat(respuesta.getHeader("Content-Disposition")).startsWith("attachment")
					.contains("comunicado-" + id + ".pdf");
			assertThat(respuesta.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
			assertThat(respuesta.getHeader("Content-Security-Policy")).contains("sandbox").contains("default-src 'none'");
			assertThat(respuesta.getHeader("Cache-Control")).contains("no-store").contains("private");
			assertThat(new String(respuesta.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1))
					.startsWith("%PDF-");
		}
	}

	/** B8: el PDF de un comunicado POR_APROBAR, RECHAZADO o RETIRADO no se descarga (salvo quien lo aprueba). */
	@Test
	void adjuntoNoEnviadoResponde404() throws Exception {
		Long id = como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Programa del paseo"), pdf(false)));
		assertThat(estadoDescarga("/comunicados/" + id + "/adjunto", tutora)).as("el docente, por aprobar").isEqualTo(404);
		assertThat(mvc.perform(get("/comunicados/" + id + "/adjunto").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andReturn().getResponse().getStatus()).as("Dirección lo aprueba: lo ve").isEqualTo(200);
		assertThat(mvc.perform(get("/familia/comunicados/" + id + "/adjunto").with(UsuariosDePrueba.como(rosaEnLinea())))
				.andReturn().getResponse().getStatus()).isEqualTo(404);
		// Rechazado: nadie lo descarga.
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		bandeja.rechazar(EscenarioAprobaciones.pendiente(jdbc, "comunicado", id), "El PDF tiene un código de pago");
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		assertThat(mvc.perform(get("/comunicados/" + id + "/adjunto").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, id))
				.isEqualTo("RECHAZADO");
		// Retirado: tampoco.
		Long enviado = conPdfAprobado(aLaSeccion(EmisorRol.DIRECCION, seccionA, "Programa del Día del Logro"));
		como(EscenarioCobranza.DIRECCION, () -> {
			envio.retirar(enviado, "Se adjuntó el programa equivocado");
			return null;
		});
		assertThat(mvc.perform(get("/familia/comunicados/" + enviado + "/adjunto").with(UsuariosDePrueba.como(
				rosaEnLinea()))).andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(mvc.perform(get("/comunicados/" + enviado + "/adjunto").with(UsuariosDePrueba.como(
				EscenarioCobranza.DIRECCION))).andReturn().getResponse().getStatus()).isEqualTo(404);
	}

	@Test
	void laPantallaDeEnvioNoAceptaLaCobranza() throws Exception {
		MvcResult r = mvc.perform(post("/comunicados/nuevo").with(csrf()).with(UsuariosDePrueba.como(
				EscenarioCobranza.DIRECCION)).param("emisor", "DIRECCION").param("alcance", "COLEGIO")
				.param("titulo", "Recordatorio de pensiones").param("cuerpo", "Recuerden pagar la pensión del mes.")
				.param("accion", "enviar")).andReturn();
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		assertThat(r.getResponse().getContentAsString()).contains("no se puede enviar");
		assertThat(contar("SELECT COUNT(*) FROM comunicado")).isZero();
		MvcResult previa = mvc.perform(post("/comunicados/nuevo").with(csrf()).with(UsuariosDePrueba.como(
				EscenarioCobranza.DIRECCION)).param("emisor", "DIRECCION").param("alcance", "COLEGIO")
				.param("titulo", "Día del Logro").param("cuerpo", TEXTO).param("accion", "previsualizar")).andReturn();
		assertThat(previa.getResponse().getContentAsString()).contains("Así lo verá la familia")
				.contains("nunca le pedirá pagos");
		MvcResult enviado = mvc.perform(post("/comunicados/nuevo").with(csrf()).with(UsuariosDePrueba.como(
				EscenarioCobranza.DIRECCION)).param("emisor", "DIRECCION").param("alcance", "COLEGIO")
				.param("titulo", "Día del Logro").param("cuerpo", TEXTO).param("requiereConfirmacion", "true")
				.param("accion", "enviar")).andReturn();
		assertThat(enviado.getResponse().getRedirectedUrl()).startsWith("/comunicados/");
		assertThat(contar("SELECT COUNT(*) FROM comunicado WHERE estado = 'ENVIADO'")).isEqualTo(1);
	}

	private int estadoDescarga(String ruta, pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario quien)
			throws Exception {
		return mvc.perform(get(ruta).with(UsuariosDePrueba.como(quien))).andReturn().getResponse().getStatus();
	}
}
