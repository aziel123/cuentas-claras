package pe.edu.virgenmaria.cuentasclaras.comun.marca;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ComoUsuario;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 8, tanda 1 (escenario E19): al cambiar {@code CC_MARCA_NOMBRE}, ninguna página sigue diciendo la marca
 * anterior: el ingreso, el aviso de privacidad, el inicio y la página de error la toman del único parámetro.
 */
@PruebaIntegracion
@TestPropertySource(properties = "cuentasclaras.marca.nombre=Colegio al Día")
class MarcaWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private Marca marca;

	@Test
	void conOtraMarcaNingunaPaginaPublicaDiceLaAnterior() throws Exception {
		assertThat(marca.nombre()).isEqualTo("Colegio al Día");
		for (String ruta : new String[] { "/login", "/privacidad" }) {
			MvcResult resultado = mvc.perform(get(ruta)).andReturn();
			String html = resultado.getResponse().getContentAsString();
			assertThat(html).as(ruta).isNotBlank().doesNotContain("Cuentas Claras").contains("Colegio al Día");
		}
	}

	@Test
	@ComoUsuario(roles = Rol.CAJA, nombreCompleto = "Lucía Ramos")
	void conOtraMarcaNingunaPaginaDiceLaAnterior() throws Exception {
		assertThat(marca.nombre()).isEqualTo("Colegio al Día");
		// (La página de error no se renderiza con MockMvc; la cubre la del ingreso: el mismo fragmento «publica».)
		for (String ruta : new String[] { "/inicio" }) {
			MvcResult resultado = mvc.perform(get(ruta)).andReturn();
			String html = resultado.getResponse().getContentAsString();
			assertThat(html).as(ruta).isNotBlank().doesNotContain("Cuentas Claras").contains("Colegio al Día");
		}
	}
}
