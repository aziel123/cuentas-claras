package pe.edu.virgenmaria.cuentasclaras.demo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Ruc;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.ServicioPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada.Accion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.SimuladorPagos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * QA del sprint 8 (sección 8; decisiones 126 y 127; E20 a E23): la demo con datos ficticios coherentes, que nunca cobra ni
 * guarda secretos reales, con «DEMO · SIN VALOR» también en el portal de la familia y un colegio por visitante.
 *
 * <p>Escenarios (Dado / Cuando / Entonces):
 * <ul>
 *   <li>Dada una demo recién creada, cuando se revisan sus cifras, entonces cada pago tiene su boleta simulada por el mismo
 *       monto (escala 2), su aviso a la familia y ninguna cuota está pagada de más.</li>
 *   <li>Dado el apoderado de la demo, cuando entra a su portal, entonces su familia ya tiene un pago con su boleta y su
 *       aviso (QA-S8-9).</li>
 *   <li>Dado el apoderado de la demo, cuando paga en línea (pasarela simulada) y abre su boleta en el portal, entonces dice
 *       «DEMO · SIN VALOR» y «COMPROBANTE SIMULADO».</li>
 *   <li>Dado un visitante, cuando propone en Conectores un OSE real con su token, entonces la demo no guarda el secreto
 *       (QA-S8-7).</li>
 *   <li>Dados dos visitantes, cuando la Dirección de B intenta aprobar la solicitud pendiente de A, entonces no puede.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "demo" })
class DemoQaSprint8Test {

	private static final Pattern CLAVE = Pattern.compile("data-clave-demo>.*?class=\"clave-temporal-valor num\">([a-z0-9-]+)<",
			Pattern.DOTALL);

	private static final Pattern USUARIO = Pattern.compile("<td class=\"num\">([a-z0-9]{4}\\.[a-z]+)</td>");

	private static final String TOKEN_REAL = "token-real-de-nubefact-del-prospecto-9876";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ServicioPagoEnLinea pagoEnLinea;

	@Autowired
	private SimuladorPagos simulador;

	record Demo(long colegioId, List<String> usuarios, String clave) {

		String usuario(String sufijo) {
			return usuarios.stream().filter(u -> u.endsWith("." + sufijo)).findFirst().orElseThrow();
		}
	}

	private Demo crearDemo(String ip) throws Exception {
		MvcResult resultado = mvc.perform(post("/demo").with(csrf()).with(r -> {
			r.setRemoteAddr(ip);
			return r;
		})).andExpect(status().isOk()).andExpect(view().name("demo/creada")).andReturn();
		String html = resultado.getResponse().getContentAsString();
		Matcher clave = CLAVE.matcher(html);
		assertThat(clave.find()).isTrue();
		List<String> usuarios = USUARIO.matcher(html).results().map(m -> m.group(1)).toList();
		long colegio = jdbc.queryForObject("SELECT colegio_id FROM usuario WHERE nombre_usuario = ?", Long.class,
				usuarios.getFirst());
		return new Demo(colegio, usuarios, clave.group(1));
	}

	private MockHttpSession ingresar(String usuario, String clave) throws Exception {
		MvcResult resultado = mvc.perform(formLogin("/login").userParameter("usuario").user(usuario)
				.passwordParam("clave").password(clave)).andExpect(status().is3xxRedirection()).andReturn();
		assertThat(resultado.getResponse().getRedirectedUrl()).doesNotContain("error");
		return (MockHttpSession) resultado.getRequest().getSession();
	}

	private long contar(String sql, Object... parametros) {
		return jdbc.queryForObject(sql, Long.class, parametros);
	}

	@Test
	void debeCrearDatosFicticiosCoherentesEnCadaDemo() throws Exception {
		Demo demo = crearDemo("192.0.2.81");
		long c = demo.colegioId();

		Map<String, Object> colegio = jdbc.queryForMap("SELECT ruc, razon_social, estado FROM colegio WHERE id = ?", c);
		assertThat(Ruc.valido((String) colegio.get("ruc"))).as("RUC ficticio con su dígito verificador").isTrue();
		assertThat((String) colegio.get("razon_social")).contains("ficticio");
		// Cada pago: su boleta por el mismo monto, con escala 2, simulada (sin valor ante SUNAT).
		List<Map<String, Object>> pagos = jdbc.queryForList("SELECT p.id, p.total, c.total AS boleta, c.proveedor "
				+ "FROM pago p JOIN comprobante c ON c.id = p.comprobante_id WHERE p.colegio_id = ?", c);
		assertThat(pagos).isNotEmpty().allSatisfy(p -> {
			BigDecimal total = (BigDecimal) p.get("total");
			assertThat(total.scale()).isEqualTo(2);
			assertThat(total.signum()).isPositive();
			assertThat(total).isEqualByComparingTo((BigDecimal) p.get("boleta"));
			assertThat(p.get("proveedor")).isEqualTo("SIMULADO");
		});
		// Toda boleta suma sus líneas.
		assertThat(contar("SELECT COUNT(*) FROM comprobante c WHERE c.colegio_id = ? AND c.total <> (SELECT COALESCE(SUM("
				+ "l.monto), 0) FROM comprobante_linea l WHERE l.comprobante_id = c.id)", c)).isZero();
		// Todo pago se avisó a la familia (el padre como auditor) y ningún mensaje salió por un proveedor real.
		assertThat(contar("SELECT COUNT(*) FROM pago p WHERE p.colegio_id = ? AND NOT EXISTS (SELECT 1 FROM mensaje m "
				+ "WHERE m.tipo = 'PAGO_REGISTRADO' AND m.entidad = 'pago' AND m.entidad_id = p.id)", c)).isZero();
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND proveedor IS NOT NULL "
				+ "AND proveedor <> 'SIMULADO'", c)).isZero();
		// Ninguna cuota pagada de más ni con pagos negativos.
		assertThat(contar("SELECT COUNT(*) FROM cuota WHERE colegio_id = ? AND (monto_pagado > monto OR monto_pagado < 0)",
				c)).isZero();
		// Las 6 cuentas comparten el prefijo del visitante.
		String prefijo = demo.usuarios().getFirst().substring(0, 4);
		assertThat(jdbc.queryForList("SELECT nombre_usuario FROM usuario WHERE colegio_id = ?", String.class, c))
				.hasSize(6).allMatch(u -> u.startsWith(prefijo + "."));
	}

	private Long familiaDelApoderado(Demo demo) {
		return jdbc.queryForObject("SELECT a.familia_id FROM usuario u JOIN apoderado a ON a.id = u.apoderado_id "
				+ "WHERE u.nombre_usuario = ?", Long.class, demo.usuario("apoderado"));
	}

	@Test
	@Disabled("QA-S8-9: la familia de la cuenta «apoderado» de la demo no tiene ningún pago, boleta ni aviso: quien entra "
			+ "como padre no ve el WhatsApp de su pago ni una boleta «DEMO · SIN VALOR» (el guion de venta del estudio)")
	void debeTenerLaFamiliaDelApoderadoDeLaDemoUnPagoConSuBoletaYSuAviso() throws Exception {
		Demo demo = crearDemo("192.0.2.82");
		Long familia = familiaDelApoderado(demo);

		assertThat(contar("SELECT COUNT(*) FROM pago WHERE colegio_id = ? AND familia_id = ?", demo.colegioId(), familia))
				.as("pagos de la familia del apoderado de la demo").isPositive();
		assertThat(contar("SELECT COUNT(*) FROM mensaje m JOIN pago p ON p.id = m.entidad_id AND m.entidad = 'pago' "
				+ "WHERE p.familia_id = ? AND m.tipo = 'PAGO_REGISTRADO'", familia)).isPositive();
	}

	@Test
	void debeMostrarDemoSinValorEnLaBoletaQueLaFamiliaPagaEnLineaEnLaDemo() throws Exception {
		Demo demo = crearDemo("192.0.2.86");
		Long familia = familiaDelApoderado(demo);
		Map<String, Object> cuenta = jdbc.queryForMap("SELECT id, apoderado_id FROM usuario WHERE nombre_usuario = ?",
				demo.usuario("apoderado"));
		Long cuota = jdbc.queryForObject("SELECT MIN(c.id) FROM cuota c JOIN alumno a ON a.id = c.alumno_id "
				+ "WHERE a.familia_id = ? AND c.estado IN ('PENDIENTE', 'VENCIDA', 'PARCIAL')", Long.class, familia);
		EscenarioCobranza.como(new UsuarioAutenticado((Long) cuenta.get("id"), demo.colegioId(), demo.usuario("apoderado"),
				"Apoderado de la demo", null, true, false, false, EnumSet.of(Rol.APODERADO), (Long) cuenta.get("apoderado_id")));
		try {
			BigDecimal total = pagoEnLinea.revisar(new SeleccionPagoRequest(List.of(cuota))).total();
			assertThat(total.scale()).isEqualTo(2);
			String referencia = pagoEnLinea.crearOrden(new PagoEnLineaRequest(UUID.randomUUID(), List.of(cuota), total,
					false));
			simulador.simular(referencia, Accion.YAPE);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		Long comprobante = jdbc.queryForObject("SELECT MIN(comprobante_id) FROM pago WHERE colegio_id = ? AND familia_id = ?",
				Long.class, demo.colegioId(), familia);
		assertThat(comprobante).isNotNull();

		MockHttpSession apoderado = ingresar(demo.usuario("apoderado"), demo.clave());
		mvc.perform(get("/familia/comprobantes/" + comprobante).session(apoderado)).andExpect(status().isOk())
				.andExpect(content().string(containsString("DEMO · SIN VALOR")))
				.andExpect(content().string(containsString("COMPROBANTE SIMULADO")));
	}

	@Test
	@Disabled("QA-S8-7: en la demo, un conector propuesto guarda el token real que escriba el visitante (cifrado); el "
			+ "diseño (sección 8.3, punto 2) dice que la demo descarta el secreto sin guardarlo")
	void debeDescartarElTokenRealQueUnVisitantePropongaEnConectores() throws Exception {
		Demo demo = crearDemo("192.0.2.83");
		MockHttpSession administracion = ingresar(demo.usuario("administracion"), demo.clave());

		mvc.perform(post("/conectores/ose").with(csrf()).session(administracion).param("proveedor", "NUBEFACT")
				.param("modo", "REAL").param("ruta", "https://api.nubefact.com/api/v1/cuenta-del-prospecto")
				.param("serieBoleta", "B002").param("serieFactura", "F002").param("serieNotaBoleta", "BC02")
				.param("serieNotaFactura", "FC02").param("afectacionIgv", "INAFECTO").param("secreto", TOKEN_REAL)
				.param("motivo", "Probamos la cuenta real de Nubefact en la demo")).andExpect(status().is3xxRedirection());

		assertThat(contar("SELECT COUNT(*) FROM conector_colegio WHERE colegio_id = ? AND tipo = 'OSE'", demo.colegioId()))
				.as("la propuesta se registra para mostrar el doble control").isEqualTo(1L);
		assertThat(contar("SELECT COUNT(*) FROM conector_colegio WHERE colegio_id = ? AND secreto_cifrado IS NOT NULL",
				demo.colegioId())).as("la demo no guarda secretos reales").isZero();
	}

	@Test
	void debeImpedirQueUnVisitanteApruebeLaSolicitudPendienteDeOtroVisitante() throws Exception {
		Demo a = crearDemo("192.0.2.84");
		Demo b = crearDemo("192.0.2.85");
		Long pendienteDeA = jdbc.queryForObject("SELECT MIN(id) FROM solicitud_cambio WHERE colegio_id = ? "
				+ "AND estado = 'PENDIENTE'", Long.class, a.colegioId());

		MockHttpSession direccionB = ingresar(b.usuario("direccion"), b.clave());
		int respuesta = mvc.perform(post("/aprobaciones/" + pendienteDeA + "/aprobar").with(csrf()).session(direccionB))
				.andReturn().getResponse().getStatus();
		mvc.perform(get("/aprobaciones/" + pendienteDeA).session(direccionB)).andExpect(status().isNotFound());

		assertThat(respuesta).isIn(302, 403, 404);
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, pendienteDeA))
				.isEqualTo("PENDIENTE");
	}
}
