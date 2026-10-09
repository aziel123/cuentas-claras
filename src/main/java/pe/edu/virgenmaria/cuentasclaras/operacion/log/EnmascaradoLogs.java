package pe.edu.virgenmaria.cuentasclaras.operacion.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;

/**
 * Sprint 7 (logs sin datos personales, Ley 29733): en prod y piloto cada línea de log es JSON (formato ECS) y TODO texto
 * que se escribe (mensaje, mensaje y traza de la excepción, valores del MDC) pasa por {@link Enmascarar#enTexto}: oculta
 * DNI, RUC, celulares, correos, tokens y el valor de un «Duplicate entry» de MySQL. Se registra con
 * {@code logging.structured.json.customizer}.
 */
public class EnmascaradoLogs implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

	@Override
	public void customize(JsonWriter.Members<ILoggingEvent> miembros) {
		miembros.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, Enmascarar::enTexto));
	}
}
