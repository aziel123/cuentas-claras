package pe.edu.virgenmaria.cuentasclaras.plataforma;

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
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RutasDeLaAplicacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RutasDeLaAplicacion.Ruta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.ColegioCreado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;
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
 * Sprint 8, tanda 1 (sección 3.2; E1, E2 y E32): el operador de la plataforma no tiene NINGUNA ruta de un colegio. Se
 * recorren TODAS las rutas de la aplicación (las lee de Spring MVC) con el operador en sesión, con un colegio que tiene
 * familias, alumnos y apoderados: fuera de {@code /plataforma/**}, su cambio de clave y el logo de su sesión, todo
 * responde 403, y ninguna respuesta muestra un nombre o un documento de esas familias. Su propia ficha del colegio
 * (sí/no y conteos) tampoco los muestra. Después del alta, el operador no tiene cuenta en el colegio nuevo.
 */
@PruebaIntegracion
class OperadorSinDatosTest {

	/** Lo que delataría a las familias del colegio 1 (de EscenarioCaja). */
	private static final List<String> MARCAS = List.of(EscenarioCaja.DNI_SEBASTIAN, EscenarioCaja.DNI_PEDRO, "Quispe",
			"Huamán", "Sebastián", "Flores Rojas", "912345678");

	private static final Map<String, String> PARAMETROS = RutasDeLaAplicacion.valores("motivo",
			"Prueba del operador sin datos", "texto", "Prueba del operador", "q", "Quispe", "medio", "EFECTIVO",
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
	private AltaColegio alta;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	private Usuario operador;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.sin.datos");
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void ningunaRutaDeUnColegioLeResponde() throws Exception {
		RequestPostProcessor comoOperador = UsuariosDePrueba.como(operador);
		List<String> errores = new ArrayList<>();
		int pedidas = 0;
		for (Ruta ruta : RutasDeLaAplicacion.todas(mapeos)) {
			String camino = ruta.concreta(Map.of());
			if (RutasDeLaAplicacion.publica(camino) || camino.startsWith("/plataforma")) {
				continue;
			}
			MockHttpServletRequestBuilder pedido = ruta.metodo().equals("POST") ? post(camino).with(csrf()) : get(camino);
			for (Map.Entry<String, String> p : PARAMETROS.entrySet()) {
				pedido = pedido.param(p.getKey(), p.getValue());
			}
			MvcResult resultado = mvc.perform(pedido.with(comoOperador)).andReturn();
			SecurityContextHolder.clearContext();
			pedidas++;
			int estado = resultado.getResponse().getStatus();
			String cuerpo = resultado.getResponse().getContentAsString();
			int esperado = esperado(camino, ruta.metodo());
			if (estado != esperado) {
				errores.add(ruta.clave() + " (" + camino + "): esperaba " + esperado + " y respondió " + estado);
			}
			String destino = camino.equals("/") ? "/inicio" : "/plataforma";
			if (esperado == 302 && !destino.equals(resultado.getResponse().getRedirectedUrl())) {
				errores.add(ruta.clave() + ": redirige a " + resultado.getResponse().getRedirectedUrl());
			}
			MARCAS.stream().filter(cuerpo::contains).findFirst()
					.ifPresent(m -> errores.add(ruta.clave() + ": muestra «" + m + "» de una familia"));
		}
		assertThat(errores).as("rutas de colegio que el operador alcanzó").isEmpty();
		assertThat(pedidas).as("se recorrieron todas las rutas de la aplicación").isGreaterThan(200);
	}

	@Test
	void suFichaDelColegioNoMuestraNombresDocumentosNiMontos() throws Exception {
		String cuerpo = mvc.perform(get("/plataforma/colegios/1").with(UsuariosDePrueba.como(operador))).andReturn()
				.getResponse().getContentAsString();

		assertThat(cuerpo).contains("Puesta en marcha").contains("Alumnos matriculados: 3");
		MARCAS.forEach(m -> assertThat(cuerpo).as("la ficha no muestra «" + m + "»").doesNotContain(m));
		assertThat(cuerpo).doesNotContain("S/ ");
		String lista = mvc.perform(get("/plataforma").with(UsuariosDePrueba.como(operador))).andReturn().getResponse()
				.getContentAsString();
		MARCAS.forEach(m -> assertThat(lista).doesNotContain(m));
	}

	@Test
	void despuesDelAltaNoTieneCuentaNiSesionEnElColegioNuevo() {
		UsuariosDePrueba.iniciarSesion(operador);
		ColegioCreado creado = alta.crear(EscenarioPlataforma.alta("Colegio Sin Operador", EscenarioPlataforma.RUC_B,
				"titular.sinoperador", "45678930", "987650030"), null, null);

		assertThat(jdbc.queryForList("SELECT nombre_usuario FROM usuario WHERE colegio_id = ?", String.class,
				creado.colegioId())).containsExactly("titular.sinoperador");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sesion_usuario WHERE colegio_id = ?", Long.class,
				creado.colegioId())).isZero();
		assertThat(jdbc.queryForList("SELECT DISTINCT colegio_id FROM sesion_usuario WHERE usuario_id = ?", Long.class,
				operador.getId())).containsOnly(EscenarioPlataforma.plataformaId(colegios));
	}

	/** Lo que debe responder cada ruta al operador. */
	private static int esperado(String camino, String metodo) {
		if (camino.equals("/") || camino.equals("/inicio")) {
			return 302; // a /plataforma
		}
		if (camino.equals(ModuloApp.RUTA_CAMBIAR_CLAVE)) {
			return 200;
		}
		if (camino.equals(ModuloApp.RUTA_LOGO)) {
			return 404; // la plataforma no tiene logo; nunca el de un colegio
		}
		return 403;
	}
}
