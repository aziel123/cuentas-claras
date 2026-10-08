package pe.edu.virgenmaria.cuentasclaras.comunicacion.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 5 (G14): el webhook de WhatsApp. La verificación de Meta solo con el token correcto; un aviso sin firma
 * {@code X-Hub-Signature-256} válida responde 401, no toca nada y no se audita; los avisos de entrega llegan en desorden
 * y el estado nunca retrocede. El estado de entrega no mueve dinero.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = { "cuentasclaras.mensajeria.whatsapp.secreto-app=secreto-de-la-app-de-prueba",
		"cuentasclaras.mensajeria.whatsapp.token-verificacion=token-de-verificacion-de-prueba" })
class WebhookWhatsAppTest {

	private static final String SECRETO = "secreto-de-la-app-de-prueba";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private Long mensaje;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"450.00"));
		SecurityContextHolder.clearContext();
		mensaje = jdbc.queryForObject("SELECT id FROM mensaje WHERE entidad_id = ? AND tipo = 'PAGO_REGISTRADO'",
				Long.class, pago);
		// Como si WhatsApp (real) lo hubiera aceptado.
		jdbc.update("UPDATE mensaje SET estado = 'ENVIADO', proveedor = 'WHATSAPP_CLOUD', proveedor_mensaje_id = "
				+ "'wamid.prueba.1', enviado_en = CURRENT_TIMESTAMP, intentos = 1 WHERE id = ?", mensaje);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private static String aviso(String estado) {
		return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{"
				+ "\"id\":\"wamid.prueba.1\",\"status\":\"" + estado + "\",\"timestamp\":\"1791200000\"}]}}]}]}";
	}

	private static String firma(String cuerpo, String secreto) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
	}

	private ResultActions enviar(String cuerpo, String firma) throws Exception {
		var peticion = post("/webhooks/whatsapp/1").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		if (firma != null) {
			peticion.header("X-Hub-Signature-256", firma);
		}
		return mvc.perform(peticion);
	}

	private String estado() {
		return jdbc.queryForObject("SELECT estado FROM mensaje WHERE id = ?", String.class, mensaje);
	}

	@Test
	void verificacionConTokenCorrecto() throws Exception {
		mvc.perform(get("/webhooks/whatsapp/1").param("hub.mode", "subscribe")
				.param("hub.verify_token", "token-de-verificacion-de-prueba").param("hub.challenge", "1158201444"))
				.andExpect(status().isOk()).andExpect(content().string("1158201444"));
		mvc.perform(get("/webhooks/whatsapp/1").param("hub.mode", "subscribe").param("hub.verify_token", "otro")
				.param("hub.challenge", "1158201444")).andExpect(status().isForbidden());
	}

	@Test
	void avisoSinFirmaResponde401YNoAudita() throws Exception {
		long eventos = contar(jdbc, "evento_auditoria");
		enviar(aviso("read"), null).andExpect(status().isUnauthorized());
		enviar(aviso("read"), firma(aviso("read"), "otro-secreto")).andExpect(status().isUnauthorized());
		enviar(aviso("read"), firma(aviso("delivered"), SECRETO)).andExpect(status().isUnauthorized());

		assertThat(estado()).isEqualTo("ENVIADO");
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventos);
	}

	@Test
	void entregadoYLeidoEnDesordenNoRetrocede() throws Exception {
		enviar(aviso("read"), firma(aviso("read"), SECRETO)).andExpect(status().isOk());
		assertThat(estado()).isEqualTo("LEIDO");
		enviar(aviso("delivered"), firma(aviso("delivered"), SECRETO)).andExpect(status().isOk());
		enviar(aviso("failed"), firma(aviso("failed"), SECRETO)).andExpect(status().isOk());
		assertThat(estado()).isEqualTo("LEIDO");
		assertThat(jdbc.queryForObject("SELECT leido_en FROM mensaje WHERE id = ?", java.sql.Timestamp.class, mensaje))
				.isNotNull();
		// Repetido: idempotente.
		enviar(aviso("read"), firma(aviso("read"), SECRETO)).andExpect(status().isOk());
		assertThat(estado()).isEqualTo("LEIDO");
	}

	@Test
	void unFallidoAvisadoCreaElRespaldoPorCorreo() throws Exception {
		enviar(aviso("delivered"), firma(aviso("delivered"), SECRETO)).andExpect(status().isOk());
		assertThat(estado()).isEqualTo("ENTREGADO");
		enviar(aviso("failed"), firma(aviso("failed"), SECRETO)).andExpect(status().isOk());
		assertThat(estado()).isEqualTo("FALLIDO");
		assertThat(jdbc.queryForObject("SELECT canal FROM mensaje WHERE respaldo_de_id = ?", String.class, mensaje))
				.isEqualTo("CORREO");
	}

	@Test
	void cuerpoGrandeResponde413() throws Exception {
		String grande = "{\"x\":\"" + "a".repeat(70_000) + "\"}";
		enviar(grande, firma(grande, SECRETO)).andExpect(status().is(413));
	}
}
