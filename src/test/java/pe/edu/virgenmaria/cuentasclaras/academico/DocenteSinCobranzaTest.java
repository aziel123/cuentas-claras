package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RutasDeLaAplicacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RutasDeLaAplicacion.Ruta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Sprint 9, tanda 1 (E30, H1): el docente NUNCA ve deudas, pagos, morosos ni el estado de cuenta. La base no lo impide
 * (cc_app lee todo), así que lo prueba la aplicación de tres formas independientes:
 * <ol>
 *   <li>se recorren TODAS las rutas de la aplicación (las lee de Spring MVC) con el docente en sesión: toda ruta que la
 *       matriz no le da (cobranza, caja, panel, reportes, alumnos, familias, mensajes...) responde 403;</li>
 *   <li>en las que sí le responden, ningún cuerpo tiene «S/ » ni los montos de la semilla (S/ 450 de pensión y S/ 350 de
 *       matrícula de las familias de su sección, que DEBEN esas cuotas);</li>
 *   <li>sus DTO no tienen campos de dinero ({@code DtoDocenteSinDatosTest}).</li>
 * </ol>
 */
@PruebaIntegracion
class DocenteSinCobranzaTest {

	/** Lo que delataría la cobranza de las familias de su sección (EscenarioCaja: pensión S/ 450, matrícula S/ 350). */
	static final List<String> MONTOS = List.of("S/ ", "S/&nbsp;", "450.00", "350.00", "450,00", "350,00");

	private static final Map<String, String> PARAMETROS = RutasDeLaAplicacion.valores("motivo",
			"Prueba del docente sin cobranza", "texto", "Prueba del docente", "q", "Quispe", "medio", "EFECTIVO",
			"documento", "00000000", "clave", "x");

	@Autowired
	private MockMvc mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeos;

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
		docente = tutorDe(usuarios, codificador, asignaciones, bandeja, jdbc, seccion, "tutora.seccion");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** Una cuenta DOCENTE tutora VIGENTE de la sección (propone Administración y aprueba Dirección). */
	static Usuario tutorDe(UsuarioRepository usuarios, PasswordEncoder codificador, Asignaciones asignaciones,
			BandejaAprobaciones bandeja, JdbcTemplate jdbc, Long seccion, String nombre) {
		Usuario cuenta = UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long id = asignaciones.proponer(new ProponerAsignacionRequest(seccion, cuenta.getId(), TipoAsignacion.TUTOR, null,
				"Tutoría del año aprobada por Dirección"));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
		return cuenta;
	}

	@Test
	void ningunaRutaDeCobranzaLeResponde() throws Exception {
		RequestPostProcessor comoDocente = UsuariosDePrueba.como(docente);
		List<String> errores = new ArrayList<>();
		int negadas = 0;
		int permitidas = 0;
		for (Ruta ruta : RutasDeLaAplicacion.todas(mapeos)) {
			String camino = ruta.concreta(Map.of("id", seccion.toString()));
			if (RutasDeLaAplicacion.publica(camino)) {
				continue;
			}
			MockHttpServletRequestBuilder pedido = ruta.metodo().equals("POST") ? post(camino).with(csrf()) : get(camino);
			for (Map.Entry<String, String> p : PARAMETROS.entrySet()) {
				pedido = pedido.param(p.getKey(), p.getValue());
			}
			MvcResult resultado = mvc.perform(pedido.with(comoDocente)).andReturn();
			SecurityContextHolder.clearContext();
			int estado = resultado.getResponse().getStatus();
			String cuerpo = resultado.getResponse().getContentAsString();
			boolean suya = RutasDeLaAplicacion.roles(camino).contains(Rol.DOCENTE);
			if (!suya) {
				negadas++;
				if (estado != 403) {
					errores.add(ruta.clave() + " (" + camino + "): no es del docente y respondió " + estado);
				}
				continue;
			}
			permitidas++;
			if (estado >= 500) {
				errores.add(ruta.clave() + " (" + camino + "): error del servidor " + estado);
			}
			MONTOS.stream().filter(cuerpo::contains).findFirst()
					.ifPresent(m -> errores.add(ruta.clave() + " (" + camino + "): muestra «" + m + "»"));
		}
		assertThat(errores).as("rutas de cobranza que el docente alcanzó o páginas suyas con montos").isEmpty();
		assertThat(negadas).as("se recorrieron las rutas de cobranza, caja, panel, reportes, alumnos y familias")
				.isGreaterThan(200);
		assertThat(permitidas).as("y las suyas").isGreaterThanOrEqualTo(4);
		// Las de cobranza que más importan, por nombre (por si la matriz cambiara): 403.
		for (String camino : List.of("/caja", "/panel", "/panel/morosos", "/panel/reportes", "/pensiones", "/alumnos",
				"/alumnos/familias/1", "/mensajes", "/aprobaciones", "/pagos-en-linea", "/recaudacion", "/conciliacion",
				"/comprobantes", "/descuentos", "/familia", "/academico/asignaciones")) {
			assertThat(mvc.perform(get(camino).with(comoDocente)).andReturn().getResponse().getStatus()).as(camino)
					.isEqualTo(403);
		}
	}

	@Test
	void susPantallasNoMuestranMontos() throws Exception {
		RequestPostProcessor comoDocente = UsuariosDePrueba.como(docente);
		String lista = mvc.perform(get("/academico/secciones/" + seccion).with(comoDocente)).andReturn().getResponse()
				.getContentAsString();
		// Control: la lista sí muestra a sus alumnos (las familias deben sus cuotas de 2027).
		assertThat(lista).contains("Mateo", "Valeria", "Sebastián", "Quispe Huamán");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuota WHERE estado = 'PENDIENTE'", Long.class)).isPositive();
		for (String camino : List.of("/inicio", "/academico", "/academico/secciones/" + seccion, "/cuenta/contacto")) {
			MvcResult resultado = mvc.perform(get(camino).with(comoDocente)).andReturn();
			SecurityContextHolder.clearContext();
			assertThat(resultado.getResponse().getStatus()).as(camino).isEqualTo(200);
			String cuerpo = resultado.getResponse().getContentAsString();
			MONTOS.forEach(m -> assertThat(cuerpo).as(camino + " no muestra «" + m + "»").doesNotContain(m));
			assertThat(cuerpo).as(camino).doesNotContain("Pendiente", "Vencid", "Moros");
		}
		// Control del detector: el estado de cuenta del alumno sí tiene montos (y al docente no le responde: 403, arriba).
		String ficha = mvc.perform(get("/alumnos/" + jdbc.queryForObject("SELECT MIN(id) FROM alumno", Long.class)
				+ "/estado-cuenta").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION))).andReturn().getResponse()
				.getContentAsString();
		assertThat(MONTOS.stream().anyMatch(ficha::contains)).as("el detector reconoce los montos").isTrue();
	}
}
