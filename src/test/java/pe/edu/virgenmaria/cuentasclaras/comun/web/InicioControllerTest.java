package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ComoUsuario;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Página de inicio sobre la aplicación completa.
 */
@PruebaIntegracion
class InicioControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@ComoUsuario(roles = Rol.CAJA, nombreCompleto = "Lucía Ramos")
	void laRaizLlevaAlInicio() throws Exception {
		mockMvc.perform(get("/")).andExpect(redirectedUrl("/inicio"));
	}

	@Test
	@ComoUsuario(roles = Rol.CAJA, nombreCompleto = "Lucía Ramos")
	void elInicioMuestraElColegioYElUsuarioDesdeLaBase() throws Exception {
		mockMvc.perform(get("/inicio"))
				.andExpect(status().isOk())
				.andExpect(view().name("inicio/caja"))
				.andExpect(content().string(containsString("Colegio Virgen María")))
				.andExpect(content().string(containsString("Hola, Lucía Ramos")))
				.andExpect(content().string(containsString("/css/tokens.css")));
	}

	@Test
	void unVisitanteSinSesionEsRedirigidoAlLogin() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", endsWith("/login")));
	}

	@Test
	void losEstilosSonPublicos() throws Exception {
		mockMvc.perform(get("/css/tokens.css")).andExpect(status().isOk());
	}

	@Test
	void debeExponerElHealthSinDetalles() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}
}
