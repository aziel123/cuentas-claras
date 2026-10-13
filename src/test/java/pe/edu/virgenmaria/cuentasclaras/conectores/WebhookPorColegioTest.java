package pe.edu.virgenmaria.cuentasclaras.conectores;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 8, tanda 2 (sección 3.6, E15): cada colegio con su pasarela recibe sus avisos en
 * {@code /webhooks/pasarela/{clave_publica}} (un UUID opaco, no el id del colegio), y la firma se valida con el secreto de
 * los avisos de ESE conector. Un aviso del colegio A firmado con el secreto de B: 401. Una clave desconocida o de un
 * conector que ya no está vigente: 404. Y el colegio con conector ya no recibe avisos por la ruta vieja (con el secreto
 * del entorno).
 */
@PruebaIntegracion
class WebhookPorColegioTest {

	private static final String SECRETO_A = "secreto-avisos-colegio-a-0123456789";

	private static final String SECRETO_B = "secreto-avisos-colegio-b-0123456789";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SecretosConectores secretos;

	@Autowired
	private PasarelaSimulada pasarela;

	@Autowired
	private ColegioRepository colegios;

	private String claveA;

	private String claveB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		long colegioB = colegios.saveAndFlush(Colegio.enPreparacion("Colegio B", "Colegio B S.A.C.", EscenarioPlataforma.RUC_B,
				"Av. 2", "info@b.edu.pe", EnumSet.of(Nivel.PRIMARIA), "Titular B", "45678921", null)).getId();
		claveA = pasarelaVigente(1L, SECRETO_A, "VIGENTE");
		claveB = pasarelaVigente(colegioB, SECRETO_B, "VIGENTE");
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** Un conector de pasarela SIMULADA (directo en H2) con su secreto de avisos cifrado para SU colegio. */
	private String pasarelaVigente(long colegio, String secretoAviso, String estado) {
		String clave = UUID.randomUUID().toString();
		byte[] cifrado = secretos.cifrar(Map.of(ConectorVigente.SECRETO_AVISO, secretoAviso), colegio, "PASARELA", clave);
		LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Lima"));
		jdbc.update("INSERT INTO conector_colegio (colegio_id, tipo, proveedor, modo, configuracion, secreto_cifrado, "
				+ "secreto_pista, clave_version, clave_publica, estado, vigente, propuesto_por, resuelto_por, resuelto_en, "
				+ "prueba_ok, creado_en, creado_por, actualizado_en) VALUES (?, 'PASARELA', 'SIMULADO', 'SIMULADO', ?, ?, '6789', "
				+ "1, ?, ?, ?, 'promotora.web', 'directora.web', ?, TRUE, ?, 'promotora.web', ?)", colegio,
				"{\"llavePublica\":null,\"medios\":[\"YAPE\"],\"montoMaximo\":5000.00}", cifrado, clave, estado,
				"VIGENTE".equals(estado) ? Boolean.TRUE : null, ahora, ahora, ahora);
		return clave;
	}

	private PasarelaSimulada.AvisoFirmado aviso(String evento, String secreto) {
		byte[] cuerpo = ("evento=" + evento + "&tipo=order.status.changed&referencia=NO-EXISTE&orden=SIMORD0")
				.getBytes(StandardCharsets.UTF_8);
		return pasarela.firmadoCon(new PasarelaSimulada.AvisoFirmado(cuerpo, Map.of()), secreto);
	}

	private org.springframework.test.web.servlet.ResultActions enviar(String ruta, PasarelaSimulada.AvisoFirmado aviso)
			throws Exception {
		return mvc.perform(post(ruta).contentType(MediaType.TEXT_PLAIN).content(aviso.cuerpo())
				.header(PasarelaSimulada.CABECERA_FIRMA, aviso.cabeceras().get(PasarelaSimulada.CABECERA_FIRMA)));
	}

	@Test
	void firmaDeOtroConectorResponde401() throws Exception {
		enviar("/webhooks/pasarela/" + claveA, aviso("SIMEVT-A-1", SECRETO_B)).andExpect(status().isUnauthorized())
				.andExpect(content().string("aviso no autentico"));
		// Ni con el secreto del entorno (el de la pasarela simulada global).
		PasarelaSimulada.AvisoFirmado delEntorno = new PasarelaSimulada.AvisoFirmado(aviso("SIMEVT-A-2", SECRETO_A).cuerpo(),
				Map.of(PasarelaSimulada.CABECERA_FIRMA, pasarela.firmar(aviso("SIMEVT-A-2", SECRETO_A).cuerpo())));
		enviar("/webhooks/pasarela/" + claveA, delEntorno).andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_pasarela", Long.class)).isZero();

		enviar("/webhooks/pasarela/" + claveA, aviso("SIMEVT-A-3", SECRETO_A)).andExpect(status().isOk())
				.andExpect(content().string("recibido"));
		enviar("/webhooks/pasarela/" + claveB, aviso("SIMEVT-B-1", SECRETO_B)).andExpect(status().isOk());
		assertThat(jdbc.queryForList("SELECT colegio_id FROM evento_pasarela ORDER BY id", Long.class)).hasSize(2)
				.first().isEqualTo(1L);
	}

	@Test
	void unConectorNoVigenteOUnaClaveDesconocidaResponde404() throws Exception {
		String rechazado = pasarelaVigente(1L, "secreto-avisos-rechazado-0123456789", "RECHAZADO");

		enviar("/webhooks/pasarela/" + rechazado, aviso("SIMEVT-R-1", "secreto-avisos-rechazado-0123456789"))
				.andExpect(status().isNotFound());
		enviar("/webhooks/pasarela/" + UUID.randomUUID(), aviso("SIMEVT-X-1", SECRETO_A)).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_pasarela", Long.class)).isZero();
	}

	@Test
	void conSuConectorElColegioYaNoRecibePorLaRutaVieja() throws Exception {
		byte[] cuerpo = aviso("SIMEVT-V-1", SECRETO_A).cuerpo();
		mvc.perform(post("/webhooks/pasarela/simulada/1").contentType(MediaType.TEXT_PLAIN).content(cuerpo)
				.header(PasarelaSimulada.CABECERA_FIRMA, pasarela.firmar(cuerpo)))
				.andExpect(status().isUnauthorized());
	}
}
