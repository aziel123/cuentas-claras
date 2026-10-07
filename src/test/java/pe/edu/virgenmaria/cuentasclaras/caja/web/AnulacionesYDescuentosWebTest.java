package pe.edu.virgenmaria.cuentasclaras.caja.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

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
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones.MOTIVO_ANULACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba.como;

/**
 * Las pantallas de la tanda 2 por la web: pedir anulación o corrección desde «Pagos de hoy» y desde el estado de cuenta,
 * la bandeja con el detalle para quien aprueba, la nota de crédito y el flujo de descuentos.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AnulacionesYDescuentosWebTest {

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
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias f;

	private Long marzoMateo;

	private Long pago;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		// La cuenta de la cajera existe con su nombre completo: la boleta y «Pagos de hoy» lo muestran.
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		jdbc.update("UPDATE usuario SET nombre_completo = 'Lucía Ramos' WHERE nombre_usuario = 'caja'");
		marzoMateo = cuota(jdbc, f.mateo(), "PEN-2027-03");
		pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como(CAJA);
		pago = cobro.cobrar(EscenarioCaja.efectivo(f.quispe(), List.of(marzoMateo), "450.00", "500.00"));
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void pagosDeHoyMuestranLaCajeraYPermitenPedirLaAnulacion() throws Exception {
		mvc.perform(get("/caja/hoy").with(como(CAJA)))
				.andExpect(status().isOk()).andExpect(view().name("caja/hoy"))
				.andExpect(content().string(containsString("Lucía Ramos")))
				.andExpect(content().string(containsString("Solicitar anulación")))
				.andExpect(content().string(containsString("action=\"/caja/pagos/" + pago + "/devolucion\"")))
				.andExpect(content().string(containsString("href=\"/caja/pagos/" + pago + "/correccion\"")));
		mvc.perform(get("/caja/pagos/{id}/comprobante", pago).with(como(CAJA)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Atendió: Lucía Ramos")))
				.andExpect(content().string(not(containsString("Atendió: caja"))));

		// Sin motivo no se pide.
		mvc.perform(post("/caja/pagos/{id}/devolucion", pago).with(csrf()).with(como(CAJA)).param("motivo", "no"))
				.andExpect(redirectedUrl("/caja/hoy")).andExpect(flash().attributeExists("error"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio", Long.class)).isZero();

		mvc.perform(post("/caja/pagos/{id}/devolucion", pago).with(csrf()).with(como(CAJA)).param("motivo",
						MOTIVO_ANULACION))
				.andExpect(redirectedUrl("/caja/hoy")).andExpect(flash().attributeExists("exito"));
		assertThat(EscenarioAprobaciones.pendiente(jdbc, "pago", pago)).isNotNull();
		mvc.perform(get("/caja/hoy").with(como(CAJA)))
				.andExpect(content().string(containsString("Esperando aprobación")))
				.andExpect(content().string(not(containsString("action=\"/caja/pagos/" + pago + "/devolucion\""))));
		// La cajera no entra a la bandeja.
		mvc.perform(get("/aprobaciones").with(como(CAJA))).andExpect(status().isForbidden());
		mvc.perform(post("/aprobaciones/{id}/aprobar", EscenarioAprobaciones.pendiente(jdbc, "pago", pago)).with(csrf())
				.with(como(CAJA))).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = ?", String.class, pago)).isEqualTo("VIGENTE");
	}

	@Test
	void correccionDesdeLaCajaYAprobacionEmitenNotaDeCreditoYBoletaNueva() throws Exception {
		Long marzoValeria = cuota(jdbc, f.valeria(), "PEN-2027-03");
		mvc.perform(get("/caja/pagos/{id}/correccion", pago).with(como(CAJA)))
				.andExpect(status().isOk()).andExpect(view().name("caja/correccion"))
				.andExpect(content().string(containsString("href=\"/caja/pagos/" + pago + "/correccion?familia="
						+ f.quispe() + "\"")));
		mvc.perform(get("/caja/pagos/{id}/correccion", pago).param("q", "flores").with(como(CAJA)))
				.andExpect(content().string(containsString("Sebastián Flores Rojas")));
		mvc.perform(get("/caja/pagos/{id}/correccion", pago).param("familia", f.quispe().toString()).with(como(CAJA)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Valeria Quispe Huamán")))
				.andExpect(content().string(containsString("value=\"" + marzoValeria + "\"")));
		mvc.perform(post("/caja/pagos/{id}/correccion", pago).with(csrf()).with(como(CAJA))
						.param("familiaId", f.quispe().toString()).param("cuotaIds", marzoValeria.toString())
						.param("motivo", MOTIVO_ANULACION))
				.andExpect(redirectedUrl("/caja/hoy")).andExpect(flash().attributeExists("exito"));

		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", pago);
		mvc.perform(get("/aprobaciones").with(como(DIRECCION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Anulación de pago")))
				.andExpect(content().string(containsString("class=\"detalle-solicitud\"")))
				.andExpect(content().string(containsString("Pensión marzo 2027 de Valeria Quispe Huamán")));
		mvc.perform(post("/aprobaciones/{id}/aprobar", solicitud).with(csrf()).with(como(DIRECCION)))
				.andExpect(redirectedUrl("/aprobaciones")).andExpect(flash().attributeExists("exito"));

		assertThat(jdbc.queryForList("SELECT CONCAT(serie, '-', numero) FROM comprobante ORDER BY id", String.class))
				.containsExactly("B001-1", "BC01-1", "B001-2");
		mvc.perform(get("/caja/hoy").with(como(CAJA)))
				.andExpect(content().string(containsString("Anulado")))
				.andExpect(content().string(containsString("BC01-00000001")))
				.andExpect(content().string(containsString("B001-00000002")));

		// El estado de cuenta muestra el pago anulado, su nota de crédito y el pago que lo reemplaza.
		Long nota = jdbc.queryForObject("SELECT id FROM comprobante WHERE serie = 'BC01'", Long.class);
		mvc.perform(get("/alumnos/{id}/estado-cuenta", f.mateo()).with(como(DIRECCION)))
				.andExpect(status().isOk()).andExpect(view().name("alumnos/estado-cuenta"))
				.andExpect(content().string(containsString("Nota de crédito BC01-00000001")))
				.andExpect(content().string(containsString("Lucía Ramos")));
		mvc.perform(get("/alumnos/{id}/estado-cuenta", f.valeria()).with(como(DIRECCION)))
				.andExpect(content().string(containsString("B001-00000002")))
				.andExpect(content().string(containsString("Reemplaza a B001-00000001")));
		mvc.perform(get("/alumnos/comprobantes/{id}", nota).param("alumno", f.mateo().toString()).with(como(DIRECCION)))
				.andExpect(status().isOk()).andExpect(view().name("caja/comprobante"))
				.andExpect(content().string(containsString("BC01-00000001")))
				.andExpect(content().string(containsString("Anula el comprobante B001-00000001")));
	}

	@Test
	void devolucionDesdeElEstadoDeCuentaMuestraElCelularSoloAQuienAprueba() throws Exception {
		mvc.perform(get("/alumnos/{id}/estado-cuenta", f.mateo()).with(como(ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("action=\"/alumnos/pagos/" + pago + "/devolucion?alumno="
						+ f.mateo() + "\"")))
				.andExpect(content().string(not(containsString("987 654")))).andExpect(content().string(not(containsString("987654321"))));
		mvc.perform(post("/alumnos/pagos/{id}/devolucion", pago).with(csrf()).with(como(ADMINISTRACION))
						.param("alumno", f.mateo().toString()).param("motivo", MOTIVO_ANULACION))
				.andExpect(redirectedUrl("/alumnos/" + f.mateo() + "/estado-cuenta"))
				.andExpect(flash().attributeExists("exito"));

		mvc.perform(get("/aprobaciones").with(como(PROMOTORIA)))
				.andExpect(content().string(containsString("Antes de aprobar, llama al apoderado")))
				.andExpect(content().string(containsString("+51 987 654 321")));
		mvc.perform(get("/alumnos/{id}/estado-cuenta", f.mateo()).with(como(ADMINISTRACION)))
				.andExpect(content().string(containsString("Esperando aprobación")))
				.andExpect(content().string(not(containsString("987 654")))).andExpect(content().string(not(containsString("987654321"))));
		// Promotoría aprueba, no pide anulaciones.
		mvc.perform(post("/alumnos/pagos/{id}/devolucion", pago).with(csrf()).with(como(PROMOTORIA))
				.param("alumno", f.mateo().toString()).param("motivo", MOTIVO_ANULACION))
				.andExpect(status().isForbidden());
	}

	@Test
	void descuentoPorHermanosDeAdministracionAprobadoPorPromotoria() throws Exception {
		Long abril = cuota(jdbc, f.valeria(), "PEN-2027-04");
		mvc.perform(get("/descuentos").with(como(ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(view().name("descuentos/lista"));
		mvc.perform(get("/descuentos/nuevo").param("dni", EscenarioEscolar.DNI_VALERIA).with(como(ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(view().name("descuentos/formulario"))
				.andExpect(content().string(containsString("Valeria Quispe Huamán")))
				.andExpect(content().string(containsString("Mateo Quispe Huamán")))
				.andExpect(content().string(containsString("value=\"" + abril + "\"")));
		Long valeria = f.valeria();
		mvc.perform(post("/descuentos/revisar").with(csrf()).with(como(ADMINISTRACION)).param("dni", EscenarioEscolar.DNI_VALERIA)
						.param("alumnoId", valeria.toString()).param("tipo", "HERMANOS").param("modalidad", "PORCENTAJE")
						.param("valor", "10").param("cuotaIds", abril.toString())
						.param("motivo", "Descuento del reglamento por dos hermanos").param("sustento", "Reglamento 2027"))
				.andExpect(status().isOk()).andExpect(view().name("descuentos/revisar"))
				.andExpect(content().string(containsString("S/ 45.00")))
				.andExpect(content().string(containsString("S/ 405.00")));
		mvc.perform(post("/descuentos").with(csrf()).with(como(ADMINISTRACION)).param("dni", EscenarioEscolar.DNI_VALERIA)
						.param("alumnoId", valeria.toString()).param("tipo", "HERMANOS").param("modalidad", "PORCENTAJE")
						.param("valor", "10").param("cuotaIds", abril.toString())
						.param("motivo", "Descuento del reglamento por dos hermanos").param("sustento", "Reglamento 2027")
						// Un total puesto a mano se ignora: lo calcula el sistema.
						.param("totalEstimado", "450.00"))
				.andExpect(redirectedUrl("/descuentos")).andExpect(flash().attributeExists("exito"));
		Long id = jdbc.queryForObject("SELECT id FROM descuento", Long.class);
		assertThat(jdbc.queryForObject("SELECT total_estimado FROM descuento", BigDecimal.class))
				.isEqualByComparingTo("45.00");
		mvc.perform(get("/descuentos").with(como(ADMINISTRACION)))
				.andExpect(content().string(containsString("Por aprobar")));

		// Quien lo pidió no lo aprueba; la caja no ve la pantalla.
		mvc.perform(get("/descuentos").with(como(CAJA))).andExpect(status().isForbidden());
		mvc.perform(get("/aprobaciones").with(como(PROMOTORIA)))
				.andExpect(content().string(containsString("Descuento o beca")))
				.andExpect(content().string(containsString("Se dejará de cobrar S/ 45.00")));
		mvc.perform(post("/aprobaciones/{id}/aprobar", EscenarioAprobaciones.pendiente(jdbc, "descuento", id))
				.with(csrf()).with(como(PROMOTORIA))).andExpect(redirectedUrl("/aprobaciones"))
				.andExpect(flash().attributeExists("exito"));

		// La caja ya cobra el monto con descuento.
		mvc.perform(get("/caja/familias/{id}", f.quispe()).with(como(CAJA)))
				.andExpect(content().string(containsString("405.00")));
		mvc.perform(get("/alumnos/{id}/estado-cuenta", valeria).with(como(ADMINISTRACION)))
				.andExpect(content().string(containsString("Descuento por hermanos")))
				.andExpect(content().string(containsString("10 %")));
	}
}
