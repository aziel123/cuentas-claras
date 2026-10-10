package pe.edu.virgenmaria.cuentasclaras.privacidad;

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
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.VistaMisDatos;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.MisDatos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 7, tanda 3 (Ley 29733, derecho de acceso al instante; E28): «Mis datos» muestra SOLO la familia de la cuenta en
 * sesión (nunca recibe un id), con sus apoderados, sus hijos, sus matrículas, para qué se usa cada dato, a quién se envía y
 * cuánto se guarda. Sin deudas ni nada académico. El personal no entra.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class MisDatosTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private MisDatos misDatos;

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

	private EscenarioCaja.Familias f;

	private UsuarioAutenticado rosa;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		rosa = apoderado(1L, f.rosa());
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void soloMuestraLaFamiliaDelApoderado() {
		UsuariosDePrueba.iniciarSesion(rosa);
		VistaMisDatos datos = misDatos.deMiFamilia();

		assertThat(datos.familia()).contains("Quispe");
		assertThat(datos.hijos()).extracting(VistaMisDatos.Hijo::nombre)
				.containsExactlyInAnyOrder("Mateo Quispe Huamán", "Valeria Quispe Huamán");
		assertThat(datos.hijos()).allSatisfy(h -> assertThat(h.matriculas()).isNotEmpty());
		assertThat(datos.apoderados()).extracting(VistaMisDatos.Apoderado::nombre).allMatch(n -> !n.contains("Flores"));
		assertThat(datos.apoderados()).anySatisfy(a -> {
			assertThat(a.responsableDePago()).isTrue();
			assertThat(a.documento()).contains(EscenarioEscolar.DNI_ROSA);
		});
		assertThat(datos.usos()).isNotEmpty();
		assertThat(datos.destinatarios()).extracting(VistaMisDatos.Destinatario::quien)
				.anyMatch(q -> q.contains("SUNAT")).anyMatch(q -> q.contains("WhatsApp")).anyMatch(q -> q.contains("banco"));
		assertThat(datos.plazos()).isNotEmpty();
		assertThat(datos.versionAviso()).isEqualTo("2027-01");
	}

	@Test
	void laPantallaNoMuestraOtraFamiliaNiDeudasNiNotas() throws Exception {
		mvc.perform(get("/familia/mis-datos").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString("Mis datos"), containsString("Mateo"),
						containsString("Para qué usamos tus datos"), containsString("A quién los enviamos"),
						containsString("Cuánto tiempo los guardamos"), containsString("data-imprimir"))))
				.andExpect(content().string(not(containsString("Sebastián"))))
				.andExpect(content().string(not(containsString(EscenarioCaja.DNI_SEBASTIAN))))
				.andExpect(content().string(not(containsString("S/ "))))
				.andExpect(content().string(not(containsString("Deuda vencida"))));
	}

	@Test
	void unaCuentaDeOtroColegioNoVeDatosDelColegioA() throws Exception {
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		UsuarioAutenticado deOtroColegio = apoderado(colegioB, f.rosa());
		mvc.perform(get("/familia/mis-datos").with(UsuariosDePrueba.como(deOtroColegio))).andExpect(status().isNotFound());
		UsuariosDePrueba.iniciarSesion(deOtroColegio);
		assertThatThrownBy(() -> misDatos.deMiFamilia())
				.isInstanceOf(pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException.class);
	}

	@Test
	void elPersonalNoEntraAMisDatos() throws Exception {
		mvc.perform(get("/familia/mis-datos").with(UsuariosDePrueba.como(Rol.PROMOTOR))).andExpect(status().isForbidden());
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));
		assertThatThrownBy(() -> misDatos.deMiFamilia())
				.isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
	}

	private static UsuarioAutenticado apoderado(long colegioId, Long apoderadoId) {
		return new UsuarioAutenticado(40L, colegioId, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), apoderadoId);
	}
}
