package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 10, tanda 1 (sección 8.3): el catálogo del currículo lo ve el personal (salvo Caja), de solo lectura; la
 * familia no. Una versión que no existe responde 404.
 */
@PruebaIntegracion
class CurriculoWebTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void elPersonalVeElCatalogoYLaFamiliaNo() throws Exception {
		for (Rol rol : new Rol[] { Rol.DOCENTE, Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.PROMOTOR }) {
			mvc.perform(get("/academico/curriculo").with(UsuariosDePrueba.como(rol))).andExpect(status().isOk())
					.andExpect(content().string(containsString("CNEB-2016.1")))
					.andExpect(content().string(containsString("PRI-MAT-1")))
					.andExpect(content().string(containsString("Resuelve problemas de cantidad")))
					.andExpect(content().string(containsString("Logro destacado")));
		}
		mvc.perform(get("/academico/curriculo/CNEB-2016.1").with(UsuariosDePrueba.como(Rol.DOCENTE)))
				.andExpect(status().isOk());
		mvc.perform(get("/academico/curriculo/CNEB-2099.1").with(UsuariosDePrueba.como(Rol.DOCENTE)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/academico/curriculo").with(UsuariosDePrueba.como(Rol.APODERADO)))
				.andExpect(status().isForbidden());
		// Caja solo entra a caja (mínimo acceso).
		mvc.perform(get("/academico/curriculo").with(UsuariosDePrueba.como(Rol.CAJA))).andExpect(status().isForbidden());
	}
}
