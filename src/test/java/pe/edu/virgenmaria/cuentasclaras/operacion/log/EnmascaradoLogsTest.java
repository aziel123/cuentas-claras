package pe.edu.virgenmaria.cuentasclaras.operacion.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 7 (E36 y A03): el formato de prod y piloto (ECS con {@link EnmascaradoLogs}) no deja datos personales en el
 * mensaje, en la excepción ni en el MDC, y un salto de línea dentro de un mensaje no parte la línea de log (no se puede
 * falsificar una entrada).
 */
class EnmascaradoLogsTest {

	@Test
	void elMensajeLaExcepcionYElMdcSalenEnmascarados() throws Exception {
		LoggerContext contexto = new LoggerContext();
		StructuredLogEncoder codificador = codificador(contexto);
		LoggingEvent evento = new LoggingEvent(EnmascaradoLogsTest.class.getName(), contexto.getLogger("prueba"),
				Level.ERROR, "No se pudo importar la fila del DNI 78451236 (rosa.huaman@gmail.com, +51987654321)",
				new IllegalStateException("fallo", new SQLIntegrityConstraintViolationException(
						"Duplicate entry '1-78451236' for key 'uk_apoderado_documento'")), null);
		evento.setMDCPropertyMap(Map.of(FiltroIdPeticion.MDC_ID, "a1b2c3d4e5f6", "ruta", "/activar/{colegio}/{token}"));

		String linea = new String(codificador.encode(evento), StandardCharsets.UTF_8);

		assertThat(linea).doesNotContain("78451236").doesNotContain("rosa.huaman").doesNotContain("987654321")
				.contains("Duplicate entry '********' for key 'uk_apoderado_documento'")
				.contains("a1b2c3d4e5f6").contains("/activar/{colegio}/{token}");
		JsonNode json = JsonMapper.builder().build().readTree(linea);
		assertThat(json.get("log").get("level").asString()).isEqualTo("ERROR");
	}

	@Test
	void unSaltoDeLineaNoFalsificaUnaEntrada() {
		LoggerContext contexto = new LoggerContext();
		StructuredLogEncoder codificador = codificador(contexto);
		LoggingEvent evento = new LoggingEvent(EnmascaradoLogsTest.class.getName(), contexto.getLogger("prueba"),
				Level.INFO, "usuario: x\n{\"log.level\":\"INFO\",\"message\":\"Ingreso correcto de promotora\"}", null, null);
		evento.setMDCPropertyMap(Map.of());

		String salida = new String(codificador.encode(evento), StandardCharsets.UTF_8).strip();

		assertThat(salida.lines().count()).isEqualTo(1);
		assertThat(JsonMapper.builder().build().readTree(salida).get("message").asString())
				.contains("\n{\"log.level\"");
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
