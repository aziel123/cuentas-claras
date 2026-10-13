package pe.edu.virgenmaria.cuentasclaras.operacion.salud;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA del sprint 8 (sección 3.8, decisión 131; E25): «Faltan filas» por colegio. Cada Promotoría ve las faltas de SU colegio
 * y las de toda la instalación; de los demás colegios, solo cuántas tablas.
 *
 * <p>Escenarios (Dado / Cuando / Entonces):
 * <ul>
 *   <li>Dadas faltas del colegio 10 y del 11, cuando las ve la Promotoría del colegio 1, entonces no ve ninguna de ellas
 *       (el «1» no coincide con el «10» ni con el «11»).</li>
 *   <li>Dada una alerta sin resolver de otro colegio, cuando la ve esta Promotoría, entonces solo sabe que existe.</li>
 * </ul>
 */
class PendientesRespaldoQaSprint8Test {

	@Test
	void debeOcultarALaPromotoriaDelColegioUnoLasFaltasDeLosColegiosDiezYOnce() {
		String diferencias = "pago (faltan 2) [colegio 10], cuota (faltan 1) [colegio 11], aplicacion_pago (faltan 1) [colegio 1]";

		String delUno = PendientesRespaldo.diferenciasDe(1L, diferencias);

		assertThat(delUno).startsWith("aplicacion_pago (faltan 1)").doesNotContain("pago (faltan 2)")
				.doesNotContain("cuota (faltan 1)").doesNotContain("[colegio").contains("faltas en 2 tablas de otros colegios");
		assertThat(PendientesRespaldo.diferenciasDe(10L, diferencias)).isEqualTo("pago (faltan 2); además, faltas en 2 "
				+ "tablas de otros colegios de la instalación (las revisa su Promotoría)");
	}

	@Test
	void debeMostrarSoloQueExisteLaAlertaSinResolverDeOtroColegio() {
		String diferencias = "alerta sin resolver desde el respaldo 7 [colegio 3], pago (faltan 1) [colegio 4]";

		assertThat(PendientesRespaldo.diferenciasDe(4L, diferencias)).isEqualTo("pago (faltan 1); además, faltas en 1 "
				+ "tabla de otros colegios de la instalación (las revisa su Promotoría)").doesNotContain("respaldo 7");
		assertThat(PendientesRespaldo.diferenciasDe(3L, diferencias)).startsWith("alerta sin resolver desde el respaldo 7")
				.doesNotContain("pago (faltan 1)");
	}
}
