package pe.edu.virgenmaria.cuentasclaras.aprobaciones.web;

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
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA sprint 6 (P8): aprobaciones desde el celular con dinero de por medio.
 * <ul>
 *   <li>Dada la anulación del pago que cobró la cajera, cuando ella abre el detalle en el celular o envía el POST de
 *       aprobar (con CSRF), entonces recibe 403 y la solicitud sigue pendiente. (Caja no puede tener además el rol de
 *       Dirección: lo impide {@code ReglasSegregacion} al crear la cuenta.)</li>
 *   <li>Dado un Docente, cuando abre la bandeja móvil o el detalle, entonces recibe 403.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AprobacionesMovilBordesTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Long solicitud;

	private Usuario cajera;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		EscenarioPanel.Datos datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos,
				bandeja, reloj, jdbc);
		// La cuenta «caja» es la que cobró los pagos de la semilla.
		cajera = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		UsuariosDePrueba.iniciarSesion(EscenarioCaja.CAJA);
		anulaciones.solicitarDevolucion(datos.pagoEfectivo(), EscenarioAprobaciones.MOTIVO_ANULACION);
		SecurityContextHolder.clearContext();
		solicitud = EscenarioAprobaciones.pendiente(jdbc, "pago", datos.pagoEfectivo());
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void laCajeraQueCobroNoVeNiApruebaSuAnulacionDesdeElCelular() throws Exception {
		mvc.perform(get("/aprobaciones").param("vista", "movil").with(UsuariosDePrueba.como(cajera)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/aprobaciones/" + solicitud).with(UsuariosDePrueba.como(cajera)))
				.andExpect(status().isForbidden());
		mvc.perform(post("/aprobaciones/" + solicitud + "/aprobar").with(csrf()).with(UsuariosDePrueba.como(cajera))
				.param("hablo", "true").param("telefonos", "987654321").param("volver", "movil"))
				.andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("PENDIENTE");
		assertThat(jdbc.queryForObject("SELECT estado FROM pago WHERE id = (SELECT entidad_id FROM solicitud_cambio "
				+ "WHERE id = ?)", String.class, solicitud)).isEqualTo("VIGENTE");
	}

	@Test
	void unDocenteNoVeLaBandejaMovilNiElDetalle() throws Exception {
		mvc.perform(get("/aprobaciones").param("vista", "movil").with(UsuariosDePrueba.como(Rol.DOCENTE)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/aprobaciones/" + solicitud).with(UsuariosDePrueba.como(Rol.DOCENTE)))
				.andExpect(status().isForbidden());
	}
}
