package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cuentasclaras.entorno} (correcciones del sprint 4, S4-M1). En el piloto, {@code nombre: PILOTO}: una franja
 * visible en TODAS las páginas dice que es un entorno de prueba y que los pagos en línea simulados no son dinero real.
 * En producción va vacío (sin franja). La pasarela SIMULADA en el piloto exige esta marca (VerificadorConfiguracion).
 */
@ConfigurationProperties("cuentasclaras.entorno")
public record PropiedadesEntorno(@DefaultValue("") String nombre) {

	public static final String PILOTO = "PILOTO";

	public PropiedadesEntorno {
		nombre = nombre == null ? "" : nombre.strip();
		if (nombre.length() > 20 || !nombre.matches("[A-Za-zÁÉÍÓÚÑáéíóúñ ]*")) {
			throw new IllegalArgumentException("cuentasclaras.entorno.nombre: hasta 20 letras (por ejemplo, PILOTO)");
		}
	}

	public boolean piloto() {
		return PILOTO.equalsIgnoreCase(nombre);
	}

	/** El texto de la franja global o {@code null} si no hay franja (producción). */
	public String franja() {
		if (nombre.isEmpty()) {
			return null;
		}
		return piloto() ? "PILOTO · Entorno de prueba: los pagos en línea son SIMULADOS, no son dinero real y no cambian "
				+ "las cuotas." : nombre.toUpperCase(java.util.Locale.ROOT) + " · Entorno de prueba";
	}
}
