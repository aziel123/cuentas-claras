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
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.efectivo;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * QA sprint 5 (G14 y G23): el webhook de WhatsApp. Dado un aviso de pago del colegio A ya aceptado por WhatsApp, cuando
 * llega un aviso firmado por la URL del colegio B (o de un colegio que no existe), entonces no toca el mensaje del A;
 * un «failed» repetido crea un solo respaldo; un aviso de un id desconocido no hace nada.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = { "cuentasclaras.mensajeria.whatsapp.secreto-app=secreto-de-la-app-de-prueba",
		"cuentasclaras.mensajeria.whatsapp.token-verificacion=token-de-verificacion-de-prueba" })
class WebhookAislamientoQaTest {

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
	private ColegioRepository colegios;

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
		jdbc.update("UPDATE mensaje SET estado = 'ENVIADO', proveedor = 'WHATSAPP_CLOUD', proveedor_mensaje_id = "
				+ "'wamid.qa.1', enviado_en = CURRENT_TIMESTAMP, intentos = 1 WHERE id = ?", mensaje);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private static String aviso(String id, String estado) {
		return "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"" + id + "\",\"status\":\"" + estado
				+ "\",\"timestamp\":\"1791200000\"}]}}]}]}";
	}

	private static String firma(String cuerpo) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
	}

	private ResultActions enviar(long colegio, String cuerpo) throws Exception {
		return mvc.perform(post("/webhooks/whatsapp/" + colegio).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.header("X-Hub-Signature-256", firma(cuerpo)));
	}

	private String estado() {
		return jdbc.queryForObject("SELECT estado FROM mensaje WHERE id = ?", String.class, mensaje);
	}

	@Test
	void unAvisoFirmadoPorLaUrlDeOtroColegioNoTocaElMensaje() throws Exception {
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();

		enviar(colegioB, aviso("wamid.qa.1", "failed")).andExpect(status().isOk());

		assertThat(estado()).isEqualTo("ENVIADO");
		assertThat(contar(jdbc, "mensaje WHERE respaldo_de_id = " + mensaje)).isZero();
	}

	@Test
	void unAvisoParaUnColegioQueNoExisteRespondeNoAutorizado() throws Exception {
		enviar(987_654L, aviso("wamid.qa.1", "read")).andExpect(status().isUnauthorized());

		assertThat(estado()).isEqualTo("ENVIADO");
	}

	@Test
	void unFailedRepetidoCreaUnSoloRespaldo() throws Exception {
		enviar(1L, aviso("wamid.qa.1", "failed")).andExpect(status().isOk());
		enviar(1L, aviso("wamid.qa.1", "failed")).andExpect(status().isOk());

		assertThat(estado()).isEqualTo("FALLIDO");
		assertThat(contar(jdbc, "mensaje WHERE respaldo_de_id = " + mensaje)).isLessThanOrEqualTo(1);
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'MENSAJE_FALLIDO'")).isEqualTo(1);
	}

	@Test
	void unAvisoDeUnIdDesconocidoNoCambiaNada() throws Exception {
		enviar(1L, aviso("wamid.de.otro", "read")).andExpect(status().isOk());

		assertThat(estado()).isEqualTo("ENVIADO");
	}

	@Test
	void unJsonInvalidoAunqueFirmadoNoRompeNiCambiaNada() throws Exception {
		enviar(1L, "{esto no es json").andExpect(status().isUnauthorized());

		assertThat(estado()).isEqualTo("ENVIADO");
	}
}
