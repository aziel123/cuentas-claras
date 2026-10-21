package pe.edu.virgenmaria.cuentasclaras.seguridad.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Rediseño de la interfaz: cada módulo del menú está en un solo grupo (Inicio va fuera, arriba). */
class GrupoMenuTest {

	@Test
	void cadaModuloEstaEnUnSoloGrupo() {
		for (ModuloApp modulo : ModuloApp.values()) {
			long grupos = Arrays.stream(GrupoMenu.values()).filter(g -> g.modulos().contains(modulo)).count();
			assertThat(grupos).as(modulo.name()).isEqualTo(modulo == ModuloApp.INICIO ? 0 : 1);
		}
	}
}
