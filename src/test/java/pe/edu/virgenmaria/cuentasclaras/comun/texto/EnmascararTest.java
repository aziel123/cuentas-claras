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

	/** Sprint 7 (logs sin datos personales): un texto libre de log pierde DNI, RUC, celulares, correos y tokens. */
	@Test
	void enTextoOcultaLosDatosPersonalesDeUnMensajeDeLog() {
		String texto = "Duplicate entry '1-78451236' for key 'uk_apoderado_documento'; RUC 20131312955; cel +51 987 654 321 "
				+ "o 912345678; correo rosa.huaman+colegio@gmail.com; enlace /activar/1/Qx7_aZ9-kLm2Np4Rs6Tu8Vw0Yb1Cd3Ef5Gh; "
				+ "hash " + "ab12".repeat(16) + "; DNI suelto 45678912.";
		String limpio = Enmascarar.enTexto(texto);

		assertThat(limpio).contains("Duplicate entry '********' for key 'uk_apoderado_documento'")
				.doesNotContain("78451236").doesNotContain("20131312955").doesNotContain("987 654 321")
				.doesNotContain("912345678").doesNotContain("rosa.huaman").doesNotContain("Qx7_aZ9")
				.doesNotContain("ab12ab12").doesNotContain("45678912");
		// Lo que no es un dato personal se conserva para entender el error.
		assertThat(Enmascarar.enTexto("Faltan triggers en la base: trg_partida_conciliacion_registro (código 1644)"))
				.isEqualTo("Faltan triggers en la base: trg_partida_conciliacion_registro (código 1644)");
		assertThat(Enmascarar.enTexto(null)).isNull();
	}

	@Test
	void unDatoMuyCortoNoSeMuestraEntero() {
		assertThat(Enmascarar.documento("CE", "1234")).isEqualTo("CE ****");
	}
}
