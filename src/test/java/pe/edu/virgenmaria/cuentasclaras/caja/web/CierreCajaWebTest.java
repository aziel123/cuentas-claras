package pe.edu.virgenmaria.cuentasclaras.caja.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.math.BigDecimal;
import java.util.List;

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
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba.como;

/**
 * Las pantallas de la tanda 3 por la web: cierre ciego (sin montos antes de contar), reconteo, depósito, bandeja con el
 * cierre, verificación bancaria y cajas del día.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CierreCajaWebTest {

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

	private Familias f;

	private Long yape;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		EscenarioCobranza.como(CAJA);
		cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(cuota(jdbc, f.mateo(), "PEN-2027-03")), "450.00",
				"500.00"));
		yape = cobro.cobrar(EscenarioCaja.digital(f.quispe(), List.of(cuota(jdbc, f.valeria(), "PEN-2027-03")),
				MedioPago.YAPE, "YP123987", "450.00"));
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cierreCiegoConReconteoYDeposito() throws Exception {
		// Antes de contar: ningún monto (ni el esperado ni lo cobrado).
		mvc.perform(get("/caja/cierre").with(como(CAJA)))
				.andExpect(status().isOk()).andExpect(view().name("caja/cierre"))
				.andExpect(content().string(containsString("data-paso=\"conteo\"")))
				.andExpect(content().string(containsString("name=\"denominaciones[B200]\"")))
				.andExpect(content().string(not(containsString("450.00"))))
				.andExpect(content().string(not(containsString("900.00"))));

		mvc.perform(post("/caja/cierre/conteo").with(csrf()).with(como(CAJA)).param("contado", "400.00")
						// Un «esperado» agregado a mano al formulario se ignora.
						.param("esperado", "400.00"))
				.andExpect(redirectedUrl("/caja/cierre")).andExpect(flash().attributeExists("advertencia"));
		mvc.perform(get("/caja/cierre").with(como(CAJA)))
				.andExpect(content().string(containsString("data-paso=\"reconteo\"")))
				.andExpect(content().string(containsString("No coincide con lo registrado")))
				.andExpect(content().string(not(containsString("450.00"))))
				.andExpect(content().string(not(containsString("400.00"))));

		// Reconteo por billetes (el servidor suma 2 × 200): faltante de S/ 50.
		mvc.perform(post("/caja/cierre/reconteo").with(csrf()).with(como(CAJA)).param("denominaciones[B200]", "2")
						.param("explicacion", "Faltan cincuenta soles, no sé en qué momento"))
				.andExpect(redirectedUrl("/caja/cierre")).andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT denominaciones FROM cierre_caja", String.class)).isEqualTo("B200×2");
		mvc.perform(get("/caja/cierre").with(como(CAJA)))
				.andExpect(content().string(containsString("data-paso=\"resultado\"")))
				.andExpect(content().string(containsString("faltante de S/ 50.00")))
				.andExpect(content().string(containsString("Registrado (esperado)")))
				.andExpect(content().string(containsString("La caja de hoy ya se cerró")))
				.andExpect(content().string(containsString("data-paso=\"deposito\"")));

		Long caja = jdbc.queryForObject("SELECT id FROM caja_diaria", Long.class);
		mvc.perform(post("/caja/cierre/deposito").with(csrf()).with(como(CAJA)).param("cajaId", caja.toString())
						.param("cuenta", "BCP Soles 191-XXXXXXX-0-XX").param("numeroOperacion", "OP-123456")
						.param("fecha", "2026-10-02").param("monto", "400.00"))
				.andExpect(redirectedUrl("/caja/cierre")).andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT monto FROM deposito_caja", BigDecimal.class)).isEqualByComparingTo("400.00");
		mvc.perform(get("/caja/cierre").with(como(CAJA)))
				.andExpect(content().string(containsString("Depositado S/ 400.00")))
				.andExpect(content().string(not(containsString("data-paso=\"deposito\""))));
	}

	@Test
	void bandejaPideComentarioParaAprobarUnCierreConDiferencia() throws Exception {
		mvc.perform(post("/caja/cierre/conteo").with(csrf()).with(como(CAJA)).param("contado", "500.00"));
		mvc.perform(post("/caja/cierre/reconteo").with(csrf()).with(como(CAJA)).param("contado", "500.00")
				.param("explicacion", "Sobran cincuenta soles, no sé de quién son"));
		Long cierre = jdbc.queryForObject("SELECT id FROM cierre_caja", Long.class);
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "cierre_caja", cierre);

		mvc.perform(get("/aprobaciones").with(como(DIRECCION)))
				.andExpect(content().string(containsString("Cierre de caja")))
				.andExpect(content().string(containsString("Sobrante de S/ 50.00")))
				.andExpect(content().string(containsString("modal-aprobar-" + solicitud)))
				.andExpect(content().string(containsString("name=\"comentario\"")))
				.andExpect(content().string(containsString("Observar")));
		mvc.perform(post("/aprobaciones/{id}/aprobar", solicitud).with(csrf()).with(como(DIRECCION)))
				.andExpect(redirectedUrl("/aprobaciones")).andExpect(flash().attributeExists("error"));
		mvc.perform(post("/aprobaciones/{id}/aprobar", solicitud).with(csrf()).with(como(DIRECCION))
						.param("comentario", "Era el pago de una familia que pagó sin boleta; se registró hoy"))
				.andExpect(redirectedUrl("/aprobaciones")).andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT estado FROM cierre_caja", String.class)).isEqualTo("APROBADO");
	}

	@Test
	void verificacionBancariaPorAdministracion() throws Exception {
		mvc.perform(get("/conciliacion").with(como(ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(view().name("conciliacion/verificacion"))
				.andExpect(content().string(containsString("YP123987")))
				.andExpect(content().string(containsString("action=\"/conciliacion/pagos/" + yape + "\"")))
				.andExpect(content().string(containsString("No aparece")));
		// Promotoría ve, pero no verifica.
		mvc.perform(get("/conciliacion").with(como(PROMOTORIA)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("YP123987")))
				.andExpect(content().string(not(containsString("action=\"/conciliacion/pagos/"))));
		mvc.perform(post("/conciliacion/pagos/{id}", yape).with(csrf()).with(como(PROMOTORIA))
				.param("resultado", "ENCONTRADO")).andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/pagos/{id}", yape).with(csrf()).with(como(ADMINISTRACION))
				.param("resultado", "NO_ENCONTRADO")).andExpect(flash().attributeExists("error"));
		mvc.perform(post("/conciliacion/pagos/{id}", yape).with(csrf()).with(como(ADMINISTRACION))
						.param("resultado", "ENCONTRADO"))
				.andExpect(redirectedUrl("/conciliacion")).andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT resultado FROM verificacion_bancaria", String.class)).isEqualTo("ENCONTRADO");
		mvc.perform(get("/conciliacion").with(como(ADMINISTRACION)))
				.andExpect(content().string(containsString("Encontrado")))
				.andExpect(content().string(not(containsString("action=\"/conciliacion/pagos/"))));
	}

	@Test
	void cajasDelDiaParaPromotoriaYDireccion() throws Exception {
		Long caja = jdbc.queryForObject("SELECT id FROM caja_diaria", Long.class);
		mvc.perform(get("/aprobaciones/cajas").with(como(DIRECCION)))
				.andExpect(status().isOk()).andExpect(view().name("aprobaciones/cajas"))
				.andExpect(content().string(containsString("Cajas del 02/10/2026")))
				.andExpect(content().string(containsString("Sin cerrar")))
				.andExpect(content().string(containsString("href=\"/aprobaciones/cajas/" + caja + "\"")));
		mvc.perform(get("/aprobaciones/cajas").param("fecha", "2026-10-01").with(como(PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("No hubo cajas ese día")));
		mvc.perform(get("/aprobaciones/cajas/{id}", caja).with(como(PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(view().name("aprobaciones/caja"))
				.andExpect(content().string(containsString("Yape YP123987")))
				.andExpect(content().string(containsString("Sin verificar")))
				.andExpect(content().string(containsString("Aún sin cerrar")));
		for (var quien : List.of(CAJA, ADMINISTRACION)) {
			mvc.perform(get("/aprobaciones/cajas").with(como(quien))).andExpect(status().isForbidden());
			mvc.perform(get("/aprobaciones/cajas/{id}", caja).with(como(quien))).andExpect(status().isForbidden());
		}
	}
}
