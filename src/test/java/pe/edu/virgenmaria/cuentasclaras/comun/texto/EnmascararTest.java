package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnmascararTest {

	@Test
	void enmascaraDocumentoTelefonoYCorreo() {
		assertThat(Enmascarar.documento("DNI", "78451236")).isEqualTo("DNI ****1236");
		assertThat(Enmascarar.telefono("+51987654321")).isEqualTo("+51 *** *** 321");
		assertThat(Enmascarar.telefono("+14155552671")).isEqualTo("+** *** *** 671");
		assertThat(Enmascarar.correo("rosa.huaman@gmail.com")).isEqualTo("r***@gmail.com");
	}

	@Test
	void celularPeruanoSeMuestraAgrupado() {
		assertThat(Telefono.formatear("+51987654321")).isEqualTo("+51 987 654 321");
		assertThat(Telefono.formatear("+14155552671")).isEqualTo("+14155552671");
		assertThat(Telefono.formatear(null)).isNull();
	}

	@Test
	void unDatoMuyCortoNoSeMuestraEntero() {
		assertThat(Enmascarar.documento("CE", "1234")).isEqualTo("CE ****");
	}
}
