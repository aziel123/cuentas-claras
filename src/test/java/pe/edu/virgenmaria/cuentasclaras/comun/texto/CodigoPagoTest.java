package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El código de pago (sprint 4): id con ceros a la izquierda y dígito de Luhn; un dígito mal tipeado no encaja. */
class CodigoPagoTest {

	@Test
	void codigoDeAlumnoYCuotaIdaYVuelta() {
		assertThat(CodigoPago.deAlumno(1)).hasSize(8).startsWith("0000001");
		assertThat(CodigoPago.deCuota(1)).hasSize(9).startsWith("00000001");
		for (long id : new long[] { 1, 7, 42, 1000, 123456, 9_999_999 }) {
			assertThat(CodigoPago.alumnoDe(CodigoPago.deAlumno(id))).contains(id);
		}
		assertThat(CodigoPago.cuotaDe(CodigoPago.deCuota(99_999_999))).contains(99_999_999L);
	}

	@Test
	void unDigitoCambiadoOTranspuestoSeRechaza() {
		String codigo = CodigoPago.deAlumno(1234);
		for (int i = 0; i < codigo.length(); i++) {
			char original = codigo.charAt(i);
			char otro = original == '9' ? '0' : (char) (original + 1);
			String errado = codigo.substring(0, i) + otro + codigo.substring(i + 1);
			assertThat(CodigoPago.alumnoDe(errado)).as(errado).isEmpty();
		}
		String transpuesto = codigo.substring(0, 5) + codigo.charAt(6) + codigo.charAt(5) + codigo.substring(7);
		if (!transpuesto.equals(codigo)) {
			assertThat(CodigoPago.alumnoDe(transpuesto)).isEmpty();
		}
	}

	@Test
	void seAceptaConEspaciosOGuionesYSeLeeEnDosGrupos() {
		String codigo = CodigoPago.deAlumno(16);
		String legible = CodigoPago.legible(codigo);
		assertThat(legible).matches("\\d{4} \\d{4}");
		assertThat(CodigoPago.alumnoDe(legible)).contains(16L);
		assertThat(CodigoPago.alumnoDe(legible.replace(' ', '-'))).contains(16L);
	}

	@Test
	void textosInvalidosNoDanError() {
		assertThat(CodigoPago.alumnoDe(null)).isEmpty();
		assertThat(CodigoPago.alumnoDe("")).isEmpty();
		assertThat(CodigoPago.alumnoDe("abcdefgh")).isEmpty();
		assertThat(CodigoPago.alumnoDe("123")).isEmpty();
		assertThat(CodigoPago.alumnoDe("00000000")).isEmpty();
		assertThat(CodigoPago.cuotaDe(CodigoPago.deAlumno(5))).isEmpty();
		assertThatThrownBy(() -> CodigoPago.deAlumno(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CodigoPago.deAlumno(10_000_000)).isInstanceOf(IllegalArgumentException.class);
	}
}
