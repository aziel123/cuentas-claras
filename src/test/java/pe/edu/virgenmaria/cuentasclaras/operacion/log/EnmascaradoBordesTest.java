package pe.edu.virgenmaria.cuentasclaras.operacion.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;

import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA del sprint 7 (logs sin datos personales, sección 10.2; E36). Bordes de {@link Enmascarar#enTexto} y del formato de
 * prod ({@link EnmascaradoLogs}): DNI, celulares, correos y montos dentro de mensajes de error.
 * <p>
 * Dado un mensaje de error con un dato personal escrito de una forma distinta a la de la semilla, cuando se registra con
 * el formato de prod, entonces el dato no aparece; y los montos (que no son datos personales) se conservan para que el
 * operador entienda el error.
 */
class EnmascaradoBordesTest {

	// ---------------------------------------------------------------- DNI

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "DNI 45678912 ya registrado", "DNI:45678912", "dni=45678912;", "documento 45678912.",
			"apoderado(45678912)", "valor '45678912' repetido", "DNI45678912", "[45678912]" })
	void debeOcultarElDniEnCualquierPosicionDelMensaje(String mensaje) {
		assertThat(Enmascarar.enTexto(mensaje)).doesNotContain("45678912");
	}

	@Test
	void debeOcultarElDniDeUnErrorDeH2ConLaFilaQueChoco() {
		String h2 = "Unique index or primary key violation: \"PUBLIC.UK_APODERADO_DOCUMENTO ON PUBLIC.APODERADO(COLEGIO_ID, "
				+ "TIPO_DOCUMENTO, NUMERO_DOCUMENTO) VALUES ( /* 3 */ CAST(1 AS BIGINT), 'DNI', '45678912' )\"";

		assertThat(Enmascarar.enTexto(h2)).doesNotContain("45678912").contains("UK_APODERADO_DOCUMENTO");
	}

	/**
	 * QA-S7-6. El carné de extranjería (9 dígitos en el Perú, 12 los antiguos) es un documento de identidad tan personal
	 * como el DNI y, en las cuentas del portal, es además el NOMBRE DE USUARIO del apoderado (como el DNI). Hoy solo se
	 * ocultan 8 (DNI) y 11 dígitos (RUC): un CE de 9 que no empieza en 9 sale entero.
	 */
	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "Ya existe el apoderado con CE 001234567", "documento CE-000123456789 no válido" })
	void debeOcultarElCarneDeExtranjeria(String mensaje) {
		assertThat(Enmascarar.enTexto(mensaje)).doesNotContain("001234567").doesNotContain("000123456789");
	}

	// ---------------------------------------------------------------- celulares

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "+51 987 654 321", "+51987654321", "51987654321", "987-654-321", "987 654 321",
			"(+51) 987654321", "+51-987-654-321", "cel:987654321;", "0051 987 654 321" })
	void debeOcultarElCelularEscritoComoLoEscribenLasFamilias(String celular) {
		String limpio = Enmascarar.enTexto("No se pudo enviar el aviso al " + celular + " por WhatsApp");

		assertThat(limpio.replaceAll("\\D", "")).doesNotContain("987654321");
	}

	/**
	 * QA-S7-6 (mismo hallazgo). Un celular con puntos (como lo escriben muchas familias en el formulario) o con el prefijo
	 * internacional 0051 sale entero.
	 */
	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "987.654.321", "0051987654321" })
	void debeOcultarElCelularConPuntosOConPrefijoInternacional(String celular) {
		String limpio = Enmascarar.enTexto("No se pudo enviar el aviso al " + celular + " por WhatsApp");

		assertThat(limpio.replaceAll("\\D", "")).doesNotContain("987654321");
	}

	// ---------------------------------------------------------------- correos

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "ROSA.HUAMAN@GMAIL.COM", "rosa.huaman+colegio@gmail.com", "<rosa_huaman@colegio.edu.pe>",
			"mailto:rosa-huaman@hotmail.com", "'rosa.huaman@gmail.com'" })
	void debeOcultarElCorreoEnMayusculasAliasOEntreSignos(String correo) {
		String limpio = Enmascarar.enTexto("Rebotó el correo " + correo + ": buzón lleno");

		assertThat(limpio.toLowerCase()).doesNotContain("rosa").doesNotContain("huaman");
	}

	@Test
	void debeOcultarElCorreoDeUnDuplicateEntryCompuesto() {
		String mysql = "Duplicate entry '1-rosa.huaman@gmail.com' for key 'uk_usuario_correo'";

		assertThat(Enmascarar.enTexto(mysql)).isEqualTo("Duplicate entry '********' for key 'uk_usuario_correo'");
	}

	// ---------------------------------------------------------------- montos (se conservan)

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
			"No se pudo cobrar S/ 1,250.00 a la familia|S/ 1,250.00",
			"El total visto 450.00 no coincide con 500.00|450.00 no coincide con 500.00",
			"Diferencia de caja: -50.00|-50.00",
			"Monto máximo de la pasarela: 5000.00|5000.00",
			"Pensión de S/ 12,345.67 rechazada|S/ 12,345.67" })
	void debeConservarLosMontosParaEntenderElError(String mensaje, String monto) {
		assertThat(Enmascarar.enTexto(mensaje)).contains(monto);
	}

	@Test
	void debeOcultarElDniPeroConservarElMontoEnElMismoMensaje() {
		assertThat(Enmascarar.enTexto("El apoderado DNI 45678912 pagó S/ 450.00 y el celular 987654321 no recibió el aviso"))
				.isEqualTo("El apoderado DNI ******** pagó S/ 450.00 y el celular ********* no recibió el aviso");
	}

	// ---------------------------------------------------------------- formato de prod (JSON ECS)

	@Test
	void debeOcultarDniCelularYCorreoEnLaCausaAnidadaDeUnErrorDeProd() {
		LoggerContext contexto = new LoggerContext();
		StructuredLogEncoder codificador = codificador(contexto);
		Exception causa = new IllegalStateException("No se pudo registrar el cobro de S/ 450.00",
				new RuntimeException("apoderado 45678912 (+51 987 654 321, ROSA.HUAMAN@GMAIL.COM)",
						new SQLIntegrityConstraintViolationException(
								"Duplicate entry '1-DNI-45678912' for key 'uk_apoderado_documento'")));
		LoggingEvent evento = new LoggingEvent(EnmascaradoBordesTest.class.getName(), contexto.getLogger("prueba"),
				Level.ERROR, "Falló el cobro", causa, null);
		evento.setMDCPropertyMap(Map.of(FiltroIdPeticion.MDC_ID, "0a1b2c3d4e5f", "ruta", "/caja/familias/{id}"));

		String linea = new String(codificador.encode(evento), StandardCharsets.UTF_8);

		assertThat(linea).doesNotContain("45678912").doesNotContain("987 654 321").doesNotContain("ROSA.HUAMAN")
				.contains("S/ 450.00").contains("0a1b2c3d4e5f").contains("/caja/familias/{id}");
	}

	@Test
	void debeOcultarUnDatoPersonalQueLlegoAlMdc() {
		LoggerContext contexto = new LoggerContext();
		StructuredLogEncoder codificador = codificador(contexto);
		LoggingEvent evento = new LoggingEvent(EnmascaradoBordesTest.class.getName(), contexto.getLogger("prueba"),
				Level.WARN, "Intento rechazado", null, null);
		evento.setMDCPropertyMap(Map.of("usuario", "45678912", "destino", "rosa.huaman@gmail.com"));

		String linea = new String(codificador.encode(evento), StandardCharsets.UTF_8);

		assertThat(linea).doesNotContain("45678912").doesNotContain("rosa.huaman");
	}

	private static StructuredLogEncoder codificador(LoggerContext contexto) {
		Environment entorno = new MockEnvironment().withProperty("logging.structured.json.customizer",
				EnmascaradoLogs.class.getName());
		contexto.putObject(Environment.class.getName(), entorno);
		StructuredLogEncoder codificador = new StructuredLogEncoder();
		codificador.setContext(contexto);
		codificador.setFormat("ecs");
		codificador.start();
		return codificador;
	}
}
