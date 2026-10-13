package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correcciones del sprint 8 (S8-M3): el CCI corresponde al banco elegido y sus dos dígitos de control son correctos.
 *
 * <p>Escenarios (Dado / Cuando / Entonces):
 * <ul>
 *   <li>Dado un CCI del BCP bien formado, cuando se propone como BCP, entonces pasa.</li>
 *   <li>Dado el mismo CCI, cuando se propone como BBVA, entonces se rechaza (no es de ese banco).</li>
 *   <li>Dado un CCI con un dígito mal copiado, entonces se rechaza por los dígitos de control.</li>
 *   <li>Dado un CCI de un banco de la lista propuesto como «Otro banco», entonces se pide elegir ese banco.</li>
 * </ul>
 */
class CuentaInterbancariaTest {

	@Test
	void unCciDelBancoConSusDigitosDeControlPasa() {
		String cci = CuentaInterbancaria.con("002", "191", "001234567890");

		assertThat(cci).hasSize(20).startsWith("002191001234567890");
		assertThatCode(() -> CuentaInterbancaria.validar(cci, ProveedorConector.BCP)).doesNotThrowAnyException();
		assertThat(CuentaInterbancaria.problema(CuentaInterbancaria.con("011", "814", "000200123456"),
				ProveedorConector.BBVA)).isNull();
	}

	@Test
	void unCciDeOtroBancoSeRechaza() {
		String bcp = CuentaInterbancaria.con("002", "191", "001234567890");

		assertThatThrownBy(() -> CuentaInterbancaria.validar(bcp, ProveedorConector.BBVA))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es de BBVA").hasMessageContaining("011");
		assertThat(CuentaInterbancaria.problema(bcp, ProveedorConector.OTRO)).contains("elige ese banco");
		assertThat(CuentaInterbancaria.problema(CuentaInterbancaria.con("038", "100", "000000012345"),
				ProveedorConector.OTRO)).isNull();
	}

	@Test
	void unDigitoMalCopiadoSeRechazaPorLosDigitosDeControl() {
		String bueno = CuentaInterbancaria.con("003", "200", "300400500600");
		for (int i = 6; i < 18; i++) {
			char[] malo = bueno.toCharArray();
			malo[i] = (char) ('0' + (malo[i] - '0' + 1) % 10);
			assertThat(CuentaInterbancaria.problema(new String(malo), ProveedorConector.INTERBANK)).as("dígito " + i)
					.contains("dígitos de control");
		}
		assertThat(CuentaInterbancaria.problema("0031234", ProveedorConector.INTERBANK)).contains("20 dígitos");
	}

	@Test
	void elDigitoDeControlSigueLaRegla() {
		// 0·1 + 0·2 + 2·1 + 1·2 + 9·1 + 1·2 = 15 → 10 − 5 = 5.
		assertThat(CuentaInterbancaria.digitoControl("002191")).isEqualTo(5);
		// 9·1 + 9·2 (=18 → 1+8 = 9): 18 → 10 − 8 = 2.
		assertThat(CuentaInterbancaria.digitoControl("99")).isEqualTo(2);
		assertThat(CuentaInterbancaria.digitoControl("000000")).isZero();
	}
}
