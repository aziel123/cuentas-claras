package pe.edu.virgenmaria.cuentasclaras.comun.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Levanta la aplicación completa (Flyway + JPA + Thymeleaf sobre H2) y verifica la página de inicio.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InicioControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void debeMostrarElNombreDelColegioCargadoPorLaMigracion() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Colegio Virgen María")))
				.andExpect(content().string(containsString("/css/tokens.css")));
	}

	@Test
	void debeExponerElHealthSinDetalles() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}
}
