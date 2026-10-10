package pe.edu.virgenmaria.cuentasclaras.operacion.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 7 (registro de errores): agrupa cada ERROR del log por su HUELLA (la clase de la excepción y el primer marco de
 * {@code pe.edu.virgenmaria}, o el logger si no hay excepción). Nunca guarda el mensaje (puede llevar datos personales).
 * Lleva en memoria cuántos hubo hoy por huella, el último id de petición y los de la ventana reciente; lo leen
 * {@code /panel/sistema} y las alertas técnicas. Se cuelga solo del logger raíz al arrancar y se suelta al cerrar.
 */
@Component
public class ContadorErrores implements InitializingBean, DisposableBean {

	static final String PAQUETE = "pe.edu.virgenmaria.";

	private static final int MAXIMO_HUELLAS = 200;

	/** Lo que se sabe de una huella hoy. */
	public record ErrorAgrupado(String huella, long hoy, Instant primero, Instant ultimo, String ultimoIdPeticion) {
	}

	private static final class Registro {

		private long hoy;

		private Instant primero;

		private Instant ultimo;

		private String ultimoIdPeticion;

		private final Deque<Instant> recientes = new ArrayDeque<>();
	}

	private final Clock reloj;

	private final Map<String, Registro> registros = new LinkedHashMap<>();

	private LocalDate dia;

	private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
		@Override
		protected void append(ILoggingEvent evento) {
			if (evento.getLevel().isGreaterOrEqual(Level.ERROR)) {
				registrar(huella(evento), evento.getMDCPropertyMap().get(FiltroIdPeticion.MDC_ID));
			}
		}
	};

	public ContadorErrores(Clock reloj) {
		this.reloj = reloj;
		this.dia = LocalDate.now(reloj);
	}

	@Override
	public void afterPropertiesSet() {
		ILoggerFactory fabrica = LoggerFactory.getILoggerFactory();
		if (fabrica instanceof LoggerContext contexto) {
			appender.setContext(contexto);
			appender.setName("cuentas-claras-contador-errores-" + System.identityHashCode(this));
			appender.start();
			contexto.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
		}
	}

	@Override
	public void destroy() {
		if (LoggerFactory.getILoggerFactory() instanceof LoggerContext contexto) {
			contexto.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
		}
		appender.stop();
	}

	/** Registra un ERROR con su huella (también desde las pruebas). */
	public synchronized void registrar(String huella, String idPeticion) {
		Instant ahora = reloj.instant();
		reiniciarSiCambioElDia();
		Registro registro = registros.get(huella);
		if (registro == null) {
			if (registros.size() >= MAXIMO_HUELLAS) {
				huella = "(demasiadas huellas distintas)";
				registro = registros.computeIfAbsent(huella, h -> new Registro());
			}
			else {
				registro = new Registro();
				registros.put(huella, registro);
			}
		}
		registro.hoy++;
		if (registro.primero == null) {
			registro.primero = ahora;
		}
		registro.ultimo = ahora;
		if (idPeticion != null) {
			registro.ultimoIdPeticion = idPeticion;
		}
		registro.recientes.addLast(ahora);
		while (registro.recientes.size() > 1000) {
			registro.recientes.pollFirst();
		}
	}

	/** Los errores de hoy, el más reciente primero. */
	public synchronized List<ErrorAgrupado> deHoy() {
		reiniciarSiCambioElDia();
		return registros.entrySet().stream()
				.map(e -> new ErrorAgrupado(e.getKey(), e.getValue().hoy, e.getValue().primero, e.getValue().ultimo,
						e.getValue().ultimoIdPeticion))
				.sorted(Comparator.comparing(ErrorAgrupado::ultimo).reversed()).toList();
	}

	/** Cuántos ERROR de esa huella hubo en la ventana que termina ahora. */
	public synchronized long enVentana(String huella, Duration ventana) {
		Registro registro = registros.get(huella);
		if (registro == null) {
			return 0;
		}
		Instant desde = reloj.instant().minus(ventana);
		return registro.recientes.stream().filter(t -> !t.isBefore(desde)).count();
	}

	private void reiniciarSiCambioElDia() {
		LocalDate hoy = LocalDate.now(reloj.withZone(ConfiguracionTiempo.ZONA_LIMA));
		if (!hoy.equals(dia)) {
			registros.clear();
			dia = hoy;
		}
	}

	/** Clase de la excepción y primer marco del código del colegio; sin excepción, el logger. Nunca el mensaje. */
	static String huella(ILoggingEvent evento) {
		IThrowableProxy error = evento.getThrowableProxy();
		if (error == null) {
			return "ERROR en " + evento.getLoggerName();
		}
		String marco = primerMarcoPropio(error);
		return error.getClassName() + (marco == null ? "" : " en " + marco);
	}

	private static String primerMarcoPropio(IThrowableProxy error) {
		for (IThrowableProxy actual = error; actual != null; actual = actual.getCause()) {
			for (StackTraceElementProxy marco : actual.getStackTraceElementProxyArray()) {
				StackTraceElement elemento = marco.getStackTraceElement();
				if (elemento.getClassName().startsWith(PAQUETE)) {
					String clase = elemento.getClassName();
					return clase.substring(clase.lastIndexOf('.') + 1) + "." + elemento.getMethodName();
				}
			}
		}
		return null;
	}
}
