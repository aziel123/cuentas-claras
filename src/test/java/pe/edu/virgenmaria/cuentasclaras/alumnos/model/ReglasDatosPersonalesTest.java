package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validación única de los datos personales (formularios y, en la tanda 2, la importación). */
class ReglasDatosPersonalesTest {

	private static final String CAMPO = "campo";

	@ParameterizedTest
	@ValueSource(strings = { "78451236", " 78451236 ", "01234567" })
	void dniDeOchoDigitosEsValido(String numero) {
		DocumentoIdentidad documento = ReglasDatosPersonales.documento(TipoDocumento.DNI, numero, CAMPO);
		assertThat(documento.numero()).hasSize(8).isEqualTo(numero.strip());
		assertThat(documento.tipo()).isEqualTo(TipoDocumento.DNI);
	}

	@Test
	void dniDeSieteDigitosExplicaLosCerosIniciales() {
		assertThatThrownBy(() -> ReglasDatosPersonales.documento(TipoDocumento.DNI, "1234567", "numeroDocumento"))
				.isInstanceOf(DatoInvalidoException.class)
				.hasMessageContaining("8 dígitos")
				.hasMessageContaining("«1234567»")
				.hasMessageContaining("cero inicial")
				.extracting(e -> ((DatoInvalidoException) e).campo()).isEqualTo("numeroDocumento");
	}

	@ParameterizedTest
	@ValueSource(strings = { "7845123A", "123456789", "78-451236", "" })
	void dniConLetrasEsRechazado(String numero) {
		assertThatThrownBy(() -> ReglasDatosPersonales.documento(TipoDocumento.DNI, numero, CAMPO))
				.isInstanceOf(DatoInvalidoException.class);
	}

	@ParameterizedTest
	@CsvSource({ "CE, ab1234567, AB1234567", "PASAPORTE, x12345, X12345", "Pasaporte, ab9876543, AB9876543",
			"Carné de extranjería, 00112233, 00112233" })
	void ceYPasaporteSeGuardanEnMayusculas(String tipo, String numero, String esperado) {
		assertThat(ReglasDatosPersonales.documento(tipo, numero).numero()).isEqualTo(esperado);
	}

	@Test
	void documentoSinEspaciosInvisiblesNiNbsp() {
		assertThat(ReglasDatosPersonales.documento(TipoDocumento.DNI, " 7845​1236﻿ ", CAMPO).numero())
				.isEqualTo("78451236");
		assertThat(ReglasDatosPersonales.documento(TipoDocumento.DNI, "7845 1236", CAMPO).numero())
				.isEqualTo("78451236");
	}

	@ParameterizedTest
	@ValueSource(strings = { "José María", "Ñañez", "O'Brien", "D’Alessandro", "Huamán-Ccori", "Ma. del Carmen", "Ü" })
	void nombresConTildesEnieYApostrofeSonValidos(String nombre) {
		assertThat(ReglasDatosPersonales.nombre(nombre, CAMPO)).isEqualTo(nombre);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Mateo2", "Ana_María", "<script>", "Rosa;", "'Ana", "Pérez*" })
	void nombresConNumerosOSimbolosSonRechazados(String nombre) {
		assertThatThrownBy(() -> ReglasDatosPersonales.nombre(nombre, CAMPO))
				.isInstanceOf(DatoInvalidoException.class);
	}

	@Test
	void nombreDeMasDe60CaracteresEsRechazado() {
		assertThat(ReglasDatosPersonales.nombre("A".repeat(60), CAMPO)).hasSize(60);
		assertThatThrownBy(() -> ReglasDatosPersonales.nombre("A".repeat(61), CAMPO))
				.isInstanceOf(DatoInvalidoException.class).hasMessageContaining("60");
		assertThat(ReglasDatosPersonales.nombreOpcional("  ", CAMPO)).isNull();
		assertThatThrownBy(() -> ReglasDatosPersonales.nombre("  ", CAMPO)).isInstanceOf(DatoInvalidoException.class);
	}

	@Test
	void celularDeNueveDigitosSeNormalizaConMas51() {
		assertThat(ReglasDatosPersonales.telefonoWhatsapp("987654321", CAMPO)).isEqualTo("+51987654321");
		assertThat(ReglasDatosPersonales.telefonoWhatsapp("+51987654321", CAMPO)).isEqualTo("+51987654321");
		assertThat(ReglasDatosPersonales.telefonoWhatsapp("51987654321", CAMPO)).isEqualTo("+51987654321");
		assertThat(ReglasDatosPersonales.telefonoWhatsapp("", CAMPO)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "987 654 321", "987-654-321", "+51 987 654 321", "(+51) 987-654-321", " 987.654.321" })
	void celularConEspaciosYGuionesSeNormaliza(String celular) {
		assertThat(ReglasDatosPersonales.telefonoWhatsapp(celular, CAMPO)).isEqualTo("+51987654321");
	}

	@ParameterizedTest
	@ValueSource(strings = { "014567890", "4567890", "+5114567890", "(01) 456-7890", "98765432" })
	void fijoDeLimaEsRechazadoParaWhatsapp(String telefono) {
		assertThatThrownBy(() -> ReglasDatosPersonales.telefonoWhatsapp(telefono, CAMPO))
				.isInstanceOf(DatoInvalidoException.class).hasMessageContaining("fijo");
	}

	@ParameterizedTest
	@CsvSource({ "+1 415 555 2671, +14155552671", "+34 612 345 678, +34612345678" })
	void numeroExtranjeroE164EsAceptado(String telefono, String esperado) {
		assertThat(ReglasDatosPersonales.telefonoWhatsapp(telefono, CAMPO)).isEqualTo(esperado);
	}

	@Test
	void correoSeGuardaEnMinusculas() {
		assertThat(ReglasDatosPersonales.correo(" Rosa.Huaman@Gmail.COM ", CAMPO)).isEqualTo("rosa.huaman@gmail.com");
		assertThat(ReglasDatosPersonales.correo("", CAMPO)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "rosa", "rosa@", "rosa@gmail", "rosa huaman@gmail.com.", "rosá@gmail.com", "a@b..com" })
	void correoInvalidoEsRechazado(String correo) {
		assertThatThrownBy(() -> ReglasDatosPersonales.correo(correo, CAMPO))
				.isInstanceOf(DatoInvalidoException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "=HYPERLINK(\"http://x\")", "+cmd", "-1+2", "@SUM(A1)" })
	void textoQueEmpiezaConIgualEsRechazado(String texto) {
		assertThatThrownBy(() -> ReglasDatosPersonales.nombre(texto, CAMPO))
				.isInstanceOf(DatoInvalidoException.class).hasMessageContaining("No puede empezar con");
		assertThatThrownBy(() -> ReglasDatosPersonales.correo(texto + "@x.com", CAMPO))
				.isInstanceOf(DatoInvalidoException.class);
		assertThatThrownBy(() -> ReglasDatosPersonales.documento(TipoDocumento.CE, texto, CAMPO))
				.isInstanceOf(DatoInvalidoException.class);
	}

	@Test
	void apoderadoSinContactoEsRechazado() {
		assertThatThrownBy(() -> ReglasDatosPersonales.exigirContacto(null, null, CAMPO))
				.isInstanceOf(DatoInvalidoException.class).hasMessageContaining("WhatsApp o el correo");
	}

	@Test
	void fechaDeNacimientoFuturaOEdadFueraDeRangoEsRechazada() {
		LocalDate hoy = LocalDate.of(2026, 10, 2);
		assertThat(ReglasDatosPersonales.fechaNacimiento(LocalDate.of(2015, 6, 14), 2026, hoy, CAMPO))
				.isEqualTo(LocalDate.of(2015, 6, 14));
		assertThatThrownBy(() -> ReglasDatosPersonales.fechaNacimiento(LocalDate.of(2026, 10, 3), 2026, hoy, CAMPO))
				.hasMessageContaining("futura");
		assertThatThrownBy(() -> ReglasDatosPersonales.fechaNacimiento(LocalDate.of(2025, 1, 1), 2026, hoy, CAMPO))
				.hasMessageContaining("de 2 a 20 años");
		assertThatThrownBy(() -> ReglasDatosPersonales.fechaNacimiento(LocalDate.of(1989, 12, 31), 2026, hoy, CAMPO))
				.hasMessageContaining("1990");
	}

	@Test
	void edadQueNoCorrespondeAlGradoEsSoloAdvertencia() {
		assertThat(ReglasDatosPersonales.advertenciaEdad(LocalDate.of(2015, 6, 14), Grado.PRIMARIA_5, 2026)).isEmpty();
		assertThat(ReglasDatosPersonales.advertenciaEdad(LocalDate.of(2015, 6, 14), Grado.PRIMARIA_1, 2026))
				.hasValueSatisfying(a -> assertThat(a).contains("10 años").contains("1.° Primaria"));
	}
}
