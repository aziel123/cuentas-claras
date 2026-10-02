package pe.edu.virgenmaria.cuentasclaras.caja.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.RevisionCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * El cobro completo por la web: buscar → familia → revisar → cobrar → confirmación → boleta imprimible → pagos de hoy.
 * Un monto agregado a mano al formulario se ignora y el doble envío no duplica el pago.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CajaControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private Long marzoMateo;

	private Long marzoValeria;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cobroCompletoEnEfectivoConVueltoYBoletaImprimible() throws Exception {
		mvc.perform(get("/caja").param("q", "quispe").with(UsuariosDePrueba.como(CAJA)))
				.andExpect(status().isOk()).andExpect(view().name("caja/buscar"))
				.andExpect(content().string(containsString("Mateo Quispe Huamán")))
				.andExpect(content().string(containsString("href=\"/caja/familias/" + f.quispe() + "\"")))
				.andExpect(content().string(containsString("Al día")));
		mvc.perform(get("/caja/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(CAJA)))
				.andExpect(status().isOk()).andExpect(view().name("caja/familia"))
				.andExpect(content().string(containsString("Pensión marzo 2027")))
				.andExpect(content().string(containsString("name=\"cuotaIds\" value=\"" + marzoMateo + "\"")));

		MvcResult revisar = mvc.perform(post("/caja/familias/{id}/revisar", f.quispe()).with(csrf())
						.with(UsuariosDePrueba.como(CAJA)).param("cuotaIds", marzoMateo.toString(), marzoValeria.toString())
						.param("medio", "EFECTIVO"))
				.andExpect(status().isOk()).andExpect(view().name("caja/revisar"))
				.andExpect(content().string(containsString("Cobrar S/ 900.00")))
				.andExpect(content().string(containsString("Caja no decide montos")))
				.andReturn();
		RevisionCobro revision = (RevisionCobro) revisar.getModelAndView().getModel().get("revision");

		// Un «total» agregado a mano al formulario (para registrar menos) se ignora: el pago es por S/ 900.00.
		MockHttpServletRequestBuilder cobrar = post("/caja/pagos").with(csrf()).with(UsuariosDePrueba.como(CAJA))
				.param("clave", revision.clave().toString()).param("familiaId", f.quispe().toString())
				.param("cuotaIds", marzoMateo.toString(), marzoValeria.toString()).param("medio", "EFECTIVO")
				.param("totalVisto", "900.00").param("recibido", "1000").param("comprobante", "BOLETA")
				.param("receptorApoderadoId", f.rosa().toString()).param("ruc", "").param("razonSocial", "")
				.param("total", "1.00").param("monto", "1.00");
		MvcResult cobrado = mvc.perform(cobrar).andExpect(status().is3xxRedirection()).andReturn();
		String confirmacion = cobrado.getResponse().getRedirectedUrl();
		assertThat(confirmacion).matches("/caja/pagos/\\d+");
		Long pagoId = Long.valueOf(confirmacion.substring(confirmacion.lastIndexOf('/') + 1));
		assertThat(jdbc.queryForObject("SELECT total FROM pago WHERE id = ?", BigDecimal.class, pagoId))
				.isEqualByComparingTo("900.00");

		// Doble envío (doble clic o recarga): el mismo pago, sin duplicar.
		mvc.perform(cobrar).andExpect(redirectedUrl(confirmacion));
		assertThat(contar(jdbc, "pago")).isEqualTo(1);
		assertThat(contar(jdbc, "comprobante")).isEqualTo(1);

		mvc.perform(get(confirmacion).with(UsuariosDePrueba.como(CAJA)))
				.andExpect(status().isOk()).andExpect(view().name("caja/confirmacion"))
				.andExpect(content().string(containsString("Pago registrado · B001-00000001")))
				.andExpect(content().string(containsString("Entrega S/ 100.00 de vuelto")))
				.andExpect(content().string(containsString("Comprobante simulado: aún sin envío a SUNAT")))
				.andExpect(content().string(containsString("Imprimir boleta")));
		mvc.perform(get(confirmacion + "/comprobante").param("imprimir", "true").with(UsuariosDePrueba.como(CAJA)))
				.andExpect(status().isOk()).andExpect(view().name("caja/comprobante"))
				.andExpect(content().string(containsString("COMPROBANTE SIMULADO · SIN VALOR TRIBUTARIO")))
				.andExpect(content().string(containsString("B001-00000001")))
				.andExpect(content().string(containsString("Rosa Huamán Ccori")))
				.andExpect(content().string(containsString("data-imprimir-al-abrir")))
				.andExpect(content().string(containsString("src=\"/js/imprimir.js\"")));
		mvc.perform(get("/caja/hoy").with(UsuariosDePrueba.como(CAJA)))
				.andExpect(status().isOk()).andExpect(view().name("caja/hoy"))
				.andExpect(content().string(containsString("B001-00000001")))
				.andExpect(content().string(containsString("Caja no puede borrar ni anular pagos")))
				// Con la caja abierta, la cajera no ve el efectivo esperado ni totales.
				.andExpect(content().string(not(containsString("esperado"))))
				.andExpect(content().string(not(containsString("Total"))));
	}

	@Test
	void montoCambiadoVuelveALaRevisionConLaMismaClave() throws Exception {
		String clave = java.util.UUID.randomUUID().toString();
		mvc.perform(post("/caja/pagos").with(csrf()).with(UsuariosDePrueba.como(CAJA)).param("clave", clave)
						.param("familiaId", f.quispe().toString()).param("cuotaIds", marzoMateo.toString(), marzoValeria.toString())
						.param("medio", "EFECTIVO").param("totalVisto", "450.00").param("recibido", "450")
						.param("comprobante", "BOLETA"))
				.andExpect(status().isOk()).andExpect(view().name("caja/revisar"))
				.andExpect(content().string(containsString("El monto cambió desde que lo revisaste (ahora es S/ 900.00)")))
				.andExpect(content().string(containsString("value=\"" + clave + "\"")))
				.andExpect(content().string(containsString("Cobrar S/ 900.00")));
		assertThat(contar(jdbc, "pago")).isZero();
	}

	@Test
	void erroresDelFormularioSeMuestranSinRegistrar() throws Exception {
		// Sin cuotas: vuelve a la familia con el aviso.
		mvc.perform(post("/caja/familias/{id}/revisar", f.quispe()).with(csrf()).with(UsuariosDePrueba.como(CAJA))
						.param("medio", "EFECTIVO"))
				.andExpect(redirectedUrl("/caja/familias/" + f.quispe()))
				.andExpect(flash().attribute("error", "Elige al menos una cuota."));
		// Yape sin número de operación: vuelve a revisar con el error.
		mvc.perform(post("/caja/pagos").with(csrf()).with(UsuariosDePrueba.como(CAJA))
						.param("clave", java.util.UUID.randomUUID().toString()).param("familiaId", f.quispe().toString())
						.param("cuotaIds", marzoMateo.toString()).param("medio", "YAPE").param("totalVisto", "450.00")
						.param("comprobante", "BOLETA").param("numeroOperacion", ""))
				.andExpect(status().isOk()).andExpect(view().name("caja/revisar"))
				.andExpect(content().string(containsString("número de operación")));
		// RUC con letras: lo rechaza la validación del formulario.
		mvc.perform(post("/caja/pagos").with(csrf()).with(UsuariosDePrueba.como(CAJA))
						.param("clave", java.util.UUID.randomUUID().toString()).param("familiaId", f.quispe().toString())
						.param("cuotaIds", marzoMateo.toString()).param("medio", "EFECTIVO").param("totalVisto", "450.00")
						.param("recibido", "450").param("comprobante", "FACTURA").param("ruc", "2013131295X"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("El RUC tiene 11 dígitos.")));
		assertThat(contar(jdbc, "pago")).isZero();
	}
}
