package pe.edu.virgenmaria.cuentasclaras.pasarela.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.RevisionPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.simulada.PasarelaSimulada;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 4, tanda 1: el apoderado paga desde el celular (pasarela simulada) y el personal lo ve. Además, el webhook de la
 * pasarela: sin sesión ni CSRF, solo POST, con firma; todo lo demás se rechaza.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class PagoEnLineaWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private PasarelaSimulada pasarela;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private UsuarioAutenticado rosa;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private String pagarDesdeLaWeb(Long cuotaId) throws Exception {
		MvcResult revision = mvc.perform(post("/familia/pagar/revisar").with(csrf()).with(UsuariosDePrueba.como(rosa))
				.param("cuotaIds", cuotaId.toString()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("PAGO SIMULADO")))
				.andReturn();
		RevisionPagoEnLinea r = (RevisionPagoEnLinea) revision.getModelAndView().getModel().get("revision");
		MvcResult creada = mvc.perform(post("/familia/pagar").with(csrf()).with(UsuariosDePrueba.como(rosa))
				.param("clave", r.clave().toString()).param("cuotaIds", cuotaId.toString())
				.param("totalVisto", r.total().toPlainString()))
				.andExpect(status().is3xxRedirection()).andReturn();
		String destino = creada.getResponse().getRedirectedUrl();
		assertThat(destino).startsWith("/familia/pagos/");
		return destino.substring("/familia/pagos/".length());
	}

	@Test
	void elApoderadoPagaConYapeSimuladoYVeSuBoleta() throws Exception {
		Long marzo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		mvc.perform(get("/familia").with(UsuariosDePrueba.como(rosa)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-pantalla=\"familia-inicio\"")))
				.andExpect(content().string(containsString("PAGO SIMULADO")));

		String referencia = pagarDesdeLaWeb(marzo);
		mvc.perform(get("/familia/pagos/" + referencia).with(UsuariosDePrueba.como(rosa)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Continuar al pago")))
				.andExpect(content().string(containsString("http-equiv=\"refresh\"")));
		mvc.perform(get("/familia/pasarela-simulada/" + referencia).with(UsuariosDePrueba.como(rosa)))
				.andExpect(status().isOk());

		mvc.perform(post("/familia/pasarela-simulada/" + referencia + "/YAPE").with(csrf()).with(UsuariosDePrueba.como(rosa)))
				.andExpect(status().is3xxRedirection());

		mvc.perform(get("/familia/pagos/" + referencia).with(UsuariosDePrueba.como(rosa)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Ver mi boleta B001-")))
				.andExpect(content().string(not(containsString("http-equiv=\"refresh\""))));
		Long comprobante = jdbc.queryForObject("SELECT comprobante_id FROM pago", Long.class);
		mvc.perform(get("/familia/comprobantes/" + comprobante).with(UsuariosDePrueba.como(rosa)))
				.andExpect(status().isOk());
		assertThat(EscenarioCaja.estado(jdbc, marzo)).isEqualTo("PAGADA");

		// El personal lo ve en «Pagos en línea»; la cajera y el apoderado no entran ahí.
		mvc.perform(get("/pagos-en-linea").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(content().string(containsString(referencia)));
		Long orden = jdbc.queryForObject("SELECT id FROM orden_pago", Long.class);
		mvc.perform(get("/pagos-en-linea/" + orden).with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Monto confirmado")));
		mvc.perform(get("/comprobantes").with(UsuariosDePrueba.como(EscenarioCobranza.ADMINISTRACION)))
				.andExpect(status().isOk());
		mvc.perform(get("/pagos-en-linea").with(UsuariosDePrueba.como(EscenarioCaja.CAJA)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/pagos-en-linea").with(UsuariosDePrueba.como(rosa))).andExpect(status().isForbidden());
		mvc.perform(get("/comprobantes").with(UsuariosDePrueba.como(rosa))).andExpect(status().isForbidden());
	}

	@Test
	void otraFamiliaNoVeLaOrdenNiLaBoleta() throws Exception {
		String referencia = pagarDesdeLaWeb(cuota(jdbc, f.mateo(), "PEN-2027-03"));
		mvc.perform(post("/familia/pasarela-simulada/" + referencia + "/YAPE").with(csrf()).with(UsuariosDePrueba.como(rosa)));
		UsuarioAutenticado pedro = new UsuarioAutenticado(41L, 1L, "pedro.familia", "Pedro Flores", null, true, false,
				false, EnumSet.of(Rol.APODERADO), f.pedro());
		Long comprobante = jdbc.queryForObject("SELECT comprobante_id FROM pago", Long.class);

		mvc.perform(get("/familia/pagos/" + referencia).with(UsuariosDePrueba.como(pedro))).andExpect(status().isNotFound());
		mvc.perform(get("/familia/comprobantes/" + comprobante).with(UsuariosDePrueba.como(pedro)))
				.andExpect(status().isNotFound());
		mvc.perform(post("/familia/pasarela-simulada/" + referencia + "/CONTRACARGO").with(csrf())
				.with(UsuariosDePrueba.como(pedro))).andExpect(status().isNotFound());
	}

	@Test
	void sinCsrfNoSePaga() throws Exception {
		mvc.perform(post("/familia/pagar/revisar").with(UsuariosDePrueba.como(rosa))
				.param("cuotaIds", cuota(jdbc, f.mateo(), "PEN-2027-03").toString()))
				.andExpect(status().isForbidden());
	}

	@Test
	void elWebhookSoloAceptaAvisosFirmados() throws Exception {
		String referencia = pagarDesdeLaWeb(cuota(jdbc, f.mateo(), "PEN-2027-03"));
		PasarelaSimulada.AvisoFirmado aviso = pasarela.aviso(pasarela.proveedorOrdenIdDe(referencia), "SIMEVT-WEB-1");
		String firma = aviso.cabeceras().get(PasarelaSimulada.CABECERA_FIRMA);

		// Sin sesión ni CSRF: la firma es la que autentica.
		mvc.perform(post("/webhooks/pasarela/simulada/1").contentType(MediaType.TEXT_PLAIN).content(aviso.cuerpo())
				.header(PasarelaSimulada.CABECERA_FIRMA, "0".repeat(64)))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("Set-Cookie"));
		mvc.perform(post("/webhooks/pasarela/simulada/1").contentType(MediaType.TEXT_PLAIN).content(aviso.cuerpo())
				.header(PasarelaSimulada.CABECERA_FIRMA, firma))
				.andExpect(status().isOk());
		mvc.perform(post("/webhooks/pasarela/simulada/1").contentType(MediaType.TEXT_PLAIN).content(aviso.cuerpo())
				.header(PasarelaSimulada.CABECERA_FIRMA, firma))
				.andExpect(status().isOk()).andExpect(content().string("ya recibido"));
		mvc.perform(post("/webhooks/pasarela/simulada/999999").contentType(MediaType.TEXT_PLAIN).content(aviso.cuerpo())
				.header(PasarelaSimulada.CABECERA_FIRMA, firma))
				.andExpect(status().isNotFound());
		mvc.perform(post("/webhooks/pasarela/inventada/1").contentType(MediaType.TEXT_PLAIN).content(aviso.cuerpo()))
				.andExpect(status().isNotFound());
		mvc.perform(post("/webhooks/pasarela/simulada/1").contentType(MediaType.TEXT_PLAIN)
				.content("x".repeat(20_000).getBytes(StandardCharsets.UTF_8)))
				.andExpect(status().isPayloadTooLarge());
		// Otra cosa que no sea el POST del aviso: denegada.
		mvc.perform(get("/webhooks/pasarela/simulada/1")).andExpect(status().isForbidden());
		mvc.perform(post("/webhooks/otra-cosa")).andExpect(status().isForbidden());
		assertThat(contar(jdbc, "evento_pasarela")).isEqualTo(1);
		assertThat(contar(jdbc, "pago")).isZero();
	}
}
