package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Levanta la aplicación completa (Flyway + JPA + Thymeleaf + seguridad sobre H2) y verifica la
 * página de inicio.
 * <p>
 * TODO(sprint1-tanda2): con el login propio y el inicio por rol, cambiar {@code @WithMockUser}
 * por {@code @ComoUsuario} y verificar la vista {@code inicio/<rol>}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InicioControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser
	void debeMostrarElNombreDelColegioCargadoPorLaMigracion() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Colegio Virgen María")))
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
		mockMvc.perform(get("/css/tokens.css"))
				.andExpect(status().isOk());
	}

	@Test
	void debeExponerElHealthSinDetalles() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}
}
