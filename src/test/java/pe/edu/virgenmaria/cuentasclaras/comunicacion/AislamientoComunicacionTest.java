package pe.edu.virgenmaria.cuentasclaras.comunicacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ConsultaMensajes;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Sprint 5 (G23): el colegio B no ve ni toca los mensajes del colegio A (404); el despacho de B no envía los de A; un
 * apoderado solo ve los mensajes de SU familia; Caja no entra a la bandeja de envíos.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoComunicacionTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ConsultaMensajes consulta;

	@Autowired
	private DespachoMensajes despacho;

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

	private Familias a;

	private Long mensajeA;

	private long colegioB;

	private UsuarioAutenticado administracionB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		a = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(EscenarioCaja.CAJA);
		Long pago = cobro.cobrar(EscenarioCaja.efectivo(a.quispe(), List.of(cuota(jdbc, a.mateo(), "PEN-2027-03")),
				"450.00", "450.00"));
		SecurityContextHolder.clearContext();
		mensajeA = jdbc.queryForObject("SELECT id FROM mensaje WHERE entidad_id = ?", Long.class, pago);
		colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		administracionB = UsuariosDePrueba.autenticado(colegioB, 93L, "administracion", "Administración B", false,
				EnumSet.of(Rol.ADMINISTRACION));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void elColegioBNoVeNiTocaLosMensajesDelA() throws Exception {
		UsuariosDePrueba.iniciarSesion(administracionB);
		assertThat(consulta.bandeja().hoy()).isEmpty();
		assertThatThrownBy(() -> consulta.reintentar(mensajeA)).isInstanceOf(RecursoNoEncontradoException.class);
		SecurityContextHolder.clearContext();
		mvc.perform(post("/mensajes/" + mensajeA + "/reintentar").with(csrf()).with(UsuariosDePrueba.como(administracionB)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/mensajes").with(UsuariosDePrueba.como(administracionB))).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Pago registrado"))));

		// El despacho del colegio B no envía los mensajes del A.
		assertThat(despacho.despacharColegio(colegioB)).isZero();
		assertThat(jdbc.queryForObject("SELECT estado FROM mensaje WHERE id = ?", String.class, mensajeA))
				.isEqualTo("PENDIENTE");
		// En A, Promotoría sí lo ve.
		mvc.perform(get("/mensajes").with(UsuariosDePrueba.como(pe.edu.virgenmaria.cuentasclaras.comun.prueba
				.EscenarioCobranza.PROMOTORIA))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Pago registrado")));
	}

	@Test
	void unApoderadoSoloVeLosMensajesDeSuFamiliaYCajaNoEntraALaBandeja() throws Exception {
		UsuarioAutenticado rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa", null, true, false, false,
				EnumSet.of(Rol.APODERADO), a.rosa());
		UsuarioAutenticado pedro = new UsuarioAutenticado(41L, 1L, "pedro.familia", "Pedro", null, true, false, false,
				EnumSet.of(Rol.APODERADO), a.pedro());
		mvc.perform(get("/familia/mensajes").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Pago registrado")))
				.andExpect(content().string(containsString("S/ 450.00")));
		mvc.perform(get("/familia/mensajes").with(UsuariosDePrueba.como(pedro))).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Pago registrado"))));
		mvc.perform(get("/mensajes").with(UsuariosDePrueba.como(EscenarioCaja.CAJA))).andExpect(status().isForbidden());
		mvc.perform(get("/familia/mensajes").with(UsuariosDePrueba.como(EscenarioCaja.CAJA)))
				.andExpect(status().isForbidden());
	}
}
