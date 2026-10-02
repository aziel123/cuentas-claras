package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.PathContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ComoUsuario;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La matriz {@link ModuloApp} es la única fuente de permisos y toda ruta fuera de ella se niega.
 */
@PruebaIntegracion
class MatrizPermisosTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeos;

	@AfterEach
	void limpiar() {
		// El manejador de acceso denegado audita cada 403.
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@ParameterizedTest
	@EnumSource(Rol.class)
	void cadaRolSoloAccedeASusModulos(Rol rol) throws Exception {
		List<String> errores = new ArrayList<>();
		for (ModuloApp modulo : ModuloApp.values()) {
			int estado = mvc.perform(get(modulo.ruta()).with(UsuariosDePrueba.como(rol))).andReturn()
					.getResponse().getStatus();
			boolean permitido = modulo.permite(rol);
			// Permitido: 200, o 404 si el módulo aún no tiene controlador. Negado: 403.
			if (permitido && (estado == 403 || estado / 100 == 3)) {
				errores.add(rol + " debería entrar a " + modulo.ruta() + " y recibió " + estado);
			}
			if (!permitido && estado != 403) {
				errores.add(rol + " NO debería entrar a " + modulo.ruta() + " y recibió " + estado);
			}
		}
		assertThat(errores).isEmpty();
	}

	@Test
	void anonimoEsRedirigidoAlLoginEnRutasProtegidas() throws Exception {
		List<String> rutas = new ArrayList<>(Arrays.stream(ModuloApp.values()).map(ModuloApp::ruta).toList());
		rutas.addAll(List.of("/", ModuloApp.RUTA_CAMBIAR_CLAVE, "/ruta-que-no-existe"));
		for (String ruta : rutas) {
			mvc.perform(get(ruta))
					.andExpect(status().is3xxRedirection())
					.andExpect(header().string("Location", endsWith("/login")));
		}
	}

	@Test
	void todasLasRutasDeLosControladoresEstanCubiertasPorLaMatriz() {
		List<String> rutas = new ArrayList<>();
		for (RequestMappingInfo info : mapeos.getHandlerMethods().keySet()) {
			rutas.addAll(info.getPatternValues());
		}
		assertThat(rutas).as("se leyeron las rutas de los controladores").contains("/inicio", "/login");

		List<String> sinCubrir = rutas.stream()
				.filter(ruta -> !estaCubierta(ruta.replaceAll("\\{[^}]+}", "1")))
				.toList();
		assertThat(sinCubrir).as("rutas de controladores que la matriz no cubre (quedarían en denyAll)").isEmpty();
	}

	@Test
	@ComoUsuario(roles = Rol.PROMOTOR)
	void rutaDesconocidaEsDenegada() throws Exception {
		mvc.perform(get("/ruta-que-no-existe")).andExpect(status().isForbidden());
		mvc.perform(get("/admin")).andExpect(status().isForbidden());
		mvc.perform(get("/salir")).andExpect(status().isForbidden());
	}

	@Test
	@ComoUsuario(roles = Rol.DIRECTOR, clavePendiente = true)
	void usuarioConClavePendienteSoloPuedeCambiarSuClave() throws Exception {
		mvc.perform(get(ModuloApp.RUTA_CAMBIAR_CLAVE)).andExpect(status().isOk());
		mvc.perform(get("/inicio")).andExpect(status().isForbidden());
		mvc.perform(get("/usuarios")).andExpect(status().isForbidden());
		mvc.perform(get("/auditoria")).andExpect(status().isForbidden());
	}

	@Test
	@ComoUsuario(roles = Rol.CAJA)
	void cajaRecibe403AlCrearUsuarios() throws Exception {
		mvc.perform(get("/usuarios/nuevo")).andExpect(status().isForbidden());
		mvc.perform(post("/usuarios").with(csrf()).param("nombreUsuario", "intruso")).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "DOCENTE", "APODERADO" })
	void docenteYApoderadoReciben403EnUsuariosYAuditoria(Rol rol) throws Exception {
		for (String ruta : List.of("/usuarios", "/usuarios/1", "/auditoria")) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
	}

	@Test
	void elMenuSaleDeLaMismaMatriz() {
		assertThat(ModuloApp.para(UsuariosDePrueba.autenticado(Rol.CAJA).getAuthorities()))
				.containsExactly(ModuloApp.INICIO, ModuloApp.CAJA_COBRO);
		assertThat(ModuloApp.para(UsuariosDePrueba.autenticado(Rol.DIRECTOR, Rol.ADMINISTRACION).getAuthorities()))
				.contains(ModuloApp.USUARIOS, ModuloApp.DESCUENTOS)
				.doesNotContain(ModuloApp.CAJA_COBRO, ModuloApp.FAMILIA);
		assertThat(ModuloApp.para(UsuariosDePrueba.autenticado(1L, 1L, "x", "X", true, EnumSet.of(Rol.PROMOTOR))
				.getAuthorities())).as("con clave pendiente no hay menú").isEmpty();
	}

	private static boolean estaCubierta(String ruta) {
		if (Arrays.asList(ModuloApp.RUTAS_PUBLICAS).contains(ruta) || ModuloApp.RUTA_CAMBIAR_CLAVE.equals(ruta)) {
			return true;
		}
		PathContainer camino = PathContainer.parsePath(ruta);
		return Arrays.stream(ModuloApp.values())
				.flatMap(m -> Arrays.stream(m.patrones()))
				.anyMatch(patron -> PathPatternParser.defaultInstance.parse(patron).matches(camino));
	}
}
