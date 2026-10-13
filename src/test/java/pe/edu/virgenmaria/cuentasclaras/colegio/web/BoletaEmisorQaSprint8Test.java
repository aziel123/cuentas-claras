package pe.edu.virgenmaria.cuentasclaras.colegio.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA del sprint 8 (sección 3.7, H8 y decisión 123): la boleta lleva el logo, la razón social, el RUC y la dirección del
 * emisor, y el monto exacto con dos decimales.
 *
 * <p>Escenarios (Dado / Cuando / Entonces):
 * <ul>
 *   <li>Dado un pago con su boleta emitida por «Colegio Uno S.A.C.», cuando Promotoría cambia después la razón social y
 *       la dirección, entonces la boleta reimpresa sigue diciendo lo que se emitió (QA-S8-5).</li>
 *   <li>Dada la marca del colegio, cuando la cajera imprime la boleta, entonces ve la razón social, el RUC, la dirección y
 *       el total con escala 2.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class BoletaEmisorQaSprint8Test {

	private static final String RUC = EscenarioCaja.RUC_EJEMPLO;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	private String nombreOriginal;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		SecurityContextHolder.clearContext();
		nombreOriginal = jdbc.queryForObject("SELECT nombre FROM colegio WHERE id = 1", String.class);
		jdbc.update("UPDATE colegio SET ruc = ? WHERE id = 1", RUC);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
		jdbc.update("UPDATE colegio SET nombre = ? WHERE id = 1", nombreOriginal);
	}

	private MockMultipartHttpServletRequestBuilder ficha(String razonSocial, String direccion) {
		return multipart("/colegio/marca").with(csrf()).with(UsuariosDePrueba.como(Rol.PROMOTOR))
				.param("nombre", nombreOriginal).param("razonSocial", razonSocial).param("direccion", direccion)
				.param("correoInstitucional", "secretaria@colegio.edu.pe");
	}

	private Long cobrarMarzo() {
		EscenarioCaja.Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		Long pagoId = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(EscenarioCaja.cuota(jdbc, f.mateo(),
				"PEN-2027-03")), "450.00", "450.00"));
		SecurityContextHolder.clearContext();
		return pagoId;
	}

	@Test
	void debeMostrarEnLaBoletaElEmisorYElTotalConDosDecimales() throws Exception {
		mvc.perform(ficha("Colegio Uno S.A.C.", "Jr. Emisión 100, Lima")).andExpect(flash().attributeExists("exito"));
		Long pagoId = cobrarMarzo();

		BigDecimal total = jdbc.queryForObject("SELECT total FROM pago WHERE id = ?", BigDecimal.class, pagoId);
		assertThat(total).isEqualByComparingTo("450.00");
		assertThat(total.scale()).isEqualTo(2);
		mvc.perform(get("/caja/pagos/{id}/comprobante", pagoId).with(UsuariosDePrueba.como(EscenarioCaja.CAJA)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Colegio Uno S.A.C.")))
				.andExpect(content().string(containsString("RUC " + RUC)))
				.andExpect(content().string(containsString("Jr. Emisión 100, Lima")))
				.andExpect(content().string(containsString("450.00")));
	}

	@Test
	void debeConservarEnLaBoletaReimpresaElEmisorConQueSeEmitio() throws Exception {
		mvc.perform(ficha("Colegio Uno S.A.C.", "Jr. Emisión 100, Lima")).andExpect(flash().attributeExists("exito"));
		Long pagoId = cobrarMarzo();
		mvc.perform(ficha("Otra Razón Social E.I.R.L.", "Av. Mudanza 999, Callao")).andExpect(flash().attributeExists("exito"));

		mvc.perform(get("/caja/pagos/{id}/comprobante", pagoId).with(UsuariosDePrueba.como(EscenarioCaja.CAJA)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Colegio Uno S.A.C.")))
				.andExpect(content().string(containsString("Jr. Emisión 100, Lima")))
				.andExpect(content().string(not(containsString("Otra Razón Social E.I.R.L."))))
				.andExpect(content().string(not(containsString("Av. Mudanza 999, Callao"))));
	}
}
