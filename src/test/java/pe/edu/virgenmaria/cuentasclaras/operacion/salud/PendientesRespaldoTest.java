package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 8, tanda 3 (V30, E25): cada Promotoría ve las diferencias de SU colegio y las de toda la instalación, nunca las
 * de otro colegio (solo que existen).
 */
class PendientesRespaldoTest {

	@Test
	void cadaColegioVeSusDiferenciasYLasDeLaInstalacion() {
		String diferencias = "ancla de la bitácora (evento 12), pago (faltan 1) [colegio 2], mensaje (faltan 3) [colegio 5], "
				+ "sesion_usuario (faltan 1) [plataforma]";

		assertThat(PendientesRespaldo.diferenciasDe(2L, diferencias)).isEqualTo("ancla de la bitácora (evento 12), "
				+ "pago (faltan 1), sesion_usuario (faltan 1) [plataforma]; además, faltas en 1 tabla de otros colegios de "
				+ "la instalación (las revisa su Promotoría)");
		assertThat(PendientesRespaldo.diferenciasDe(5L, diferencias)).contains("mensaje (faltan 3)")
				.doesNotContain("pago (faltan 1)").doesNotContain("[colegio");
		assertThat(PendientesRespaldo.diferenciasDe(7L, "pago (faltan 1) [colegio 2], cuota (faltan 2) [colegio 2]"))
				.isEqualTo("además, faltas en 2 tablas de otros colegios de la instalación (las revisa su Promotoría)");
	}

	@Test
	void sinColegioOAntesDeV30SeVeTodo() {
		assertThat(PendientesRespaldo.diferenciasDe(null, "pago (faltan 1) [colegio 2]"))
				.isEqualTo("pago (faltan 1) [colegio 2]");
		assertThat(PendientesRespaldo.diferenciasDe(1L, "pago (faltan 1)")).isEqualTo("pago (faltan 1)");
		assertThat(PendientesRespaldo.diferenciasDe(1L, null)).isNull();
	}
}
