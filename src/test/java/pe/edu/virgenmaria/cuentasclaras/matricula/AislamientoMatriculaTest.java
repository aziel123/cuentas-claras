package pe.edu.virgenmaria.cuentasclaras.matricula;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioRenovacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoRequest;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioAvisosFamilia;
import pe.edu.virgenmaria.cuentasclaras.matricula.proceso.ActivacionMatriculas;
import pe.edu.virgenmaria.cuentasclaras.matricula.proceso.ReservaMatriculas;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ServicioCampanaRenovacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Sprint 5, tanda 2 (G23): el colegio B no ve ni toca las renovaciones ni los avisos de las familias del colegio A
 * (404), y los procesos de {@code sistema.matricula} de B no reservan ni activan nada de A.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoMatriculaTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioCampanaRenovacion campana;

	@Autowired
	private ServicioAvisosFamilia avisos;

	@Autowired
	private ReservaMatriculas reserva;

	@Autowired
	private ActivacionMatriculas activacion;

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

	private EscenarioRenovacion.Datos a;

	private Long renovacionA;

	private Long avisoA;

	private long colegioB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		a = EscenarioRenovacion.preparar(estructura, alumnos, planes, jdbc, true);
		como(ADMINISTRACION);
		campana.abrir(a.anio2027(), EscenarioRenovacion.RESPONDEN_HASTA);
		como(a.rosaEnLinea());
		avisoA = avisos.enviar(new AvisoRequest(TipoAvisoFamilia.OTRO, null, null, "Consulta del colegio A"));
		SecurityContextHolder.clearContext();
		renovacionA = EscenarioRenovacion.renovacionDe(jdbc, a.mateo());
		// La renovación de A queda CONFIRMADA sin reservar (como si el proceso no hubiera corrido todavía).
		jdbc.update("UPDATE renovacion_matricula SET estado = 'CONFIRMADA', canal_respuesta = 'PORTAL', "
				+ "respondido_por = 'rosa.familia', respondido_en = CURRENT_TIMESTAMP WHERE id = ?", renovacionA);
		colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private UsuarioAutenticado deB(Rol rol, long id) {
		return UsuariosDePrueba.autenticado(colegioB, id, rol.name().toLowerCase(), rol.name(), false, EnumSet.of(rol));
	}

	@Test
	void elColegioBNoVeNiTocaLasRenovacionesNiLosAvisosDelA() throws Exception {
		como(deB(Rol.DIRECTOR, 91L));
		assertThatThrownBy(() -> campana.cambiarDestino(renovacionA, a.p6A2027()))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> campana.campana(a.anio2027())).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(avisos.bandeja()).isEmpty();
		assertThatThrownBy(() -> avisos.atender(avisoA, "Respuesta de otro colegio"))
				.isInstanceOf(RecursoNoEncontradoException.class);
		SecurityContextHolder.clearContext();

		mvc.perform(post("/matricula-2027/" + renovacionA + "/presencial").with(csrf()).param("continua", "true")
				.with(UsuariosDePrueba.como(deB(Rol.ADMINISTRACION, 92L)))).andExpect(status().isNotFound());
		mvc.perform(post("/avisos-familias/" + avisoA + "/atender").with(csrf()).param("respuesta", "No es de B")
				.with(UsuariosDePrueba.como(deB(Rol.PROMOTOR, 93L)))).andExpect(status().isNotFound());
		mvc.perform(get("/avisos-familias").with(UsuariosDePrueba.como(deB(Rol.PROMOTOR, 93L)))).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Consulta del colegio A"))));

		// Los procesos de sistema.matricula del colegio B no reservan la renovación confirmada del A.
		assertThat(reserva.enColegio(colegioB)).isZero();
		assertThat(activacion.activar(colegioB)).isZero();
		assertThat(jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE id = ?", String.class, renovacionA))
				.isEqualTo("CONFIRMADA");
		// En A, sí.
		assertThat(reserva.enColegio(1L)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT estado FROM renovacion_matricula WHERE id = ?", String.class, renovacionA))
				.isEqualTo("MATRICULADA");
	}
}
