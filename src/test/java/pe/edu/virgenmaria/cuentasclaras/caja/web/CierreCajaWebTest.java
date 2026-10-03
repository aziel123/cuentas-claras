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

	/**
	 * C1: la verificación es a ciegas. Mientras está pendiente, la pantalla no muestra el número de operación registrado;
	 * Administración escribe lo que ve en el banco. Si no coincide, no se guarda nada y queda en la bitácora.
	 */
	@Test
	void verificacionBancariaAciegasPorAdministracion() throws Exception {
		mvc.perform(get("/conciliacion").with(como(ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(view().name("conciliacion/verificacion"))
				.andExpect(content().string(not(containsString("YP123987"))))
				.andExpect(content().string(containsString("action=\"/conciliacion/pagos/" + yape + "\"")))
				.andExpect(content().string(containsString("name=\"operacion\"")))
				.andExpect(content().string(containsString("name=\"monto\"")))
				.andExpect(content().string(containsString("No aparece")));
		// Promotoría ve, pero no verifica.
		mvc.perform(get("/conciliacion").with(como(PROMOTORIA)))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("YP123987"))))
				.andExpect(content().string(not(containsString("action=\"/conciliacion/pagos/"))));
		mvc.perform(post("/conciliacion/pagos/{id}", yape).with(csrf()).with(como(PROMOTORIA))
				.param("resultado", "ENCONTRADO")).andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/pagos/{id}", yape).with(csrf()).with(como(ADMINISTRACION))
				.param("resultado", "NO_ENCONTRADO")).andExpect(flash().attributeExists("error"));
		// «Encontrado» sin lo que se ve en el banco, o con un monto distinto: nada se guarda.
		mvc.perform(post("/conciliacion/pagos/{id}", yape).with(csrf()).with(como(ADMINISTRACION))
				.param("resultado", "ENCONTRADO")).andExpect(flash().attributeExists("error"));
		mvc.perform(post("/conciliacion/pagos/{id}", yape).with(csrf()).with(como(ADMINISTRACION))
						.param("resultado", "ENCONTRADO").param("operacion", "yp-123987").param("fecha", "2026-10-02")
						.param("monto", "45.00"))
				.andExpect(redirectedUrl("/conciliacion"))
				.andExpect(flash().attribute("error", containsString("no coincide")));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM verificacion_bancaria", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'VERIFICACION_NO_COINCIDE'",
				Long.class)).isEqualTo(1);
		mvc.perform(post("/conciliacion/pagos/{id}", yape).with(csrf()).with(como(ADMINISTRACION))
						.param("resultado", "ENCONTRADO").param("operacion", "yp-123987").param("fecha", "2026-10-02")
						.param("monto", "450"))
				.andExpect(redirectedUrl("/conciliacion")).andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForMap("SELECT resultado, banco_operacion FROM verificacion_bancaria"))
				.containsEntry("resultado", "ENCONTRADO").containsEntry("banco_operacion", "YP123987");
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

	/**
	 * Hallazgo 10 de QA: un conteo mayor que S/ 9,999,999.99, con demasiadas piezas o con un número que no cabe en un
	 * entero da un mensaje, no un error 500; y no cierra nada.
	 */
	@Test
	void montosMaximosDelConteoSeRechazanEnElFormulario() throws Exception {
		mvc.perform(post("/caja/cierre/conteo").with(csrf()).with(como(CAJA)).param("contado", "10000000.00"))
				.andExpect(redirectedUrl("/caja/cierre"))
				.andExpect(flash().attribute("error", containsString("hasta S/ 9,999,999.99")));
		mvc.perform(post("/caja/cierre/conteo").with(csrf()).with(como(CAJA)).param("denominaciones[B200]", "60000"))
				.andExpect(redirectedUrl("/caja/cierre")).andExpect(flash().attributeExists("error"));
		mvc.perform(post("/caja/cierre/conteo").with(csrf()).with(como(CAJA))
						.param("denominaciones[B200]", "99999999999"))
				.andExpect(redirectedUrl("/caja/cierre")).andExpect(flash().attributeExists("error"));
		assertThat(jdbc.queryForMap("SELECT estado, conteos FROM caja_diaria")).containsEntry("estado", "ABIERTA")
				.containsEntry("conteos", 0);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cierre_caja", Long.class)).isZero();
	}

	/**
	 * A2 por la web: la cajera pide la devolución con su causa; Dirección la aprueba solo marcando «Hablé con el
	 * apoderado» con su celular registrado; Administración registra la entrega en efectivo (el formulario de efectivo no
	 * envía «cuenta de origen»: antes daba un error de formato).
	 */
	@Test
	void devolucionEnEfectivoPorLaWebConLlamadaYReembolso() throws Exception {
		Long pago = jdbc.queryForObject("SELECT id FROM pago WHERE medio = 'EFECTIVO'", Long.class);
		mvc.perform(post("/caja/pagos/{id}/devolucion", pago).with(csrf()).with(como(CAJA))
						.param("causa", "COBRO_EQUIVOCADO").param("motivo", "Se cobró la pensión equivocada en ventanilla"))
				.andExpect(redirectedUrl("/caja/hoy")).andExpect(flash().attributeExists("exito"));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);
		mvc.perform(get("/aprobaciones").with(como(DIRECCION)))
				.andExpect(content().string(containsString("name=\"telefonos\"")))
				.andExpect(content().string(containsString("Hablé con el apoderado")));
		mvc.perform(post("/aprobaciones/{id}/aprobar", solicitud).with(csrf()).with(como(DIRECCION)))
				.andExpect(flash().attribute("error", containsString("Hablé con el apoderado")));
		mvc.perform(post("/aprobaciones/{id}/aprobar", solicitud).with(csrf()).with(como(DIRECCION))
						.param("hablo", "true").param("telefonos", "987 654 321"))
				.andExpect(redirectedUrl("/aprobaciones")).andExpect(flash().attributeExists("exito"));
		Long anulacion = jdbc.queryForObject("SELECT id FROM anulacion_pago", Long.class);
		mvc.perform(post("/conciliacion/devoluciones/{id}", anulacion).with(csrf()).with(como(ADMINISTRACION))
						.param("recibidoPorNombre", "Rosa Huamán Ccori").param("recibidoPorDocumento", "45678912"))
				.andExpect(redirectedUrl("/conciliacion")).andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT medio FROM reembolso", String.class)).isEqualTo("EFECTIVO");
	}
}
