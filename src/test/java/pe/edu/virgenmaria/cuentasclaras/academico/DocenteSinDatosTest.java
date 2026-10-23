package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 9, tanda 1 (E31 y E33, Ley 29733): el docente ve solo el número de orden y el nombre de los alumnos de sus
 * secciones. Ninguna de sus pantallas muestra el DNI, la fecha de nacimiento, los apoderados ni sus celulares o correos
 * (los de la semilla de EscenarioCaja), y cada vez que abre la lista de una sección queda en «Quién vio datos personales»
 * como LISTA_SECCION, con la cantidad y sin nombres.
 */
@PruebaIntegracion
class DocenteSinDatosTest {

	/** Lo que la semilla tiene de cada familia y el docente no debe ver. */
	private static final List<String> DATOS = List.of(EscenarioEscolar.DNI_MATEO, EscenarioEscolar.DNI_VALERIA,
			EscenarioEscolar.DNI_ROSA, EscenarioCaja.DNI_SEBASTIAN, EscenarioCaja.DNI_PEDRO, EscenarioEscolar.CELULAR_ROSA,
			EscenarioEscolar.CORREO_ROSA, "912345678", "14/06/2015", "2015-06-14", "03/09/2018", "21/08/2014", "Rosa",
			"Pedro", "Flores Díaz", "Huamán Ccori");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private Asignaciones asignaciones;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario docente;

	private Long seccion;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		EscenarioCaja.Familias familias = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		seccion = jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE alumno_id = ?", Long.class, familias.mateo());
		SecurityContextHolder.clearContext();
		docente = DocenteSinCobranzaTest.tutorDe(usuarios, codificador, asignaciones, bandeja, jdbc, seccion,
				"tutor.sin.datos");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void susPantallasNoMuestranDocumentosNiContactos() throws Exception {
		RequestPostProcessor comoDocente = UsuariosDePrueba.como(docente);
		for (String camino : List.of("/inicio", "/academico", "/academico/secciones/" + seccion, "/cuenta/contacto")) {
			MvcResult resultado = mvc.perform(get(camino).with(comoDocente)).andReturn();
			SecurityContextHolder.clearContext();
			assertThat(resultado.getResponse().getStatus()).as(camino).isEqualTo(200);
			String cuerpo = resultado.getResponse().getContentAsString();
			DATOS.forEach(d -> assertThat(cuerpo).as(camino + " no muestra «" + d + "»").doesNotContain(d));
		}
		// La ficha del alumno (con su DNI) no le responde.
		assertThat(mvc.perform(get("/alumnos/" + jdbc.queryForObject("SELECT MIN(id) FROM alumno", Long.class))
				.with(comoDocente)).andReturn().getResponse().getStatus()).isEqualTo(403);
		// Control del detector: Dirección, en la ficha del alumno, sí ve el DNI.
		String ficha = mvc.perform(get("/alumnos/" + jdbc.queryForObject("SELECT id FROM alumno WHERE nombres = 'Mateo'",
				Long.class)).with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION))).andReturn().getResponse()
				.getContentAsString();
		assertThat(ficha).contains(EscenarioEscolar.DNI_MATEO);
	}

	/** E33: la lista de la sección queda registrada como LISTA_SECCION, con la cantidad y sin familia ni alumno. */
	@Test
	void laListaQuedaEnQuienVioDatosPersonales() throws Exception {
		mvc.perform(get("/academico/secciones/" + seccion).with(UsuariosDePrueba.como(docente))).andReturn();
		SecurityContextHolder.clearContext();

		List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM acceso_dato_personal WHERE usuario_id = ?",
				docente.getId());
		assertThat(filas).singleElement().satisfies(f -> {
			assertThat(f.get("TIPO")).isEqualTo("LISTA_SECCION");
			assertThat(((Number) f.get("CANTIDAD")).intValue()).isEqualTo(3);
			assertThat(f.get("FAMILIA_ID")).isNull();
			assertThat(f.get("ALUMNO_ID")).isNull();
		});
		// Una sección que no es suya responde 404 y no queda registrada.
		Long otra = jdbc.queryForObject("SELECT MIN(id) FROM seccion WHERE id <> ?", Long.class, seccion);
		assertThat(mvc.perform(get("/academico/secciones/" + otra).with(UsuariosDePrueba.como(docente))).andReturn()
				.getResponse().getStatus()).isEqualTo(404);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM acceso_dato_personal WHERE usuario_id = ?", Long.class,
				docente.getId())).isEqualTo(1L);
		// Dirección la ve también, y también queda registrada.
		Usuario directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora.lista", UsuariosDePrueba.CLAVE,
				false, Rol.DIRECTOR);
		assertThat(mvc.perform(get("/academico/secciones/" + seccion).with(UsuariosDePrueba.como(directora))).andReturn()
				.getResponse().getContentAsString()).contains("Mateo");
		assertThat(jdbc.queryForObject("SELECT tipo FROM acceso_dato_personal WHERE usuario_id = ?", String.class,
				directora.getId())).isEqualTo("LISTA_SECCION");
	}
}
