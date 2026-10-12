package pe.edu.virgenmaria.cuentasclaras.comun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cuentasclaras.entorno} (correcciones del sprint 4, S4-M1). En el piloto, {@code nombre: PILOTO}: una franja
 * visible en TODAS las páginas dice que es un entorno de prueba y que los pagos en línea simulados no son dinero real.
 * En producción va vacío (sin franja). La pasarela SIMULADA en el piloto exige esta marca (VerificadorConfiguracion).
 * Sprint 8, tanda 3: en la demo, {@code nombre: DEMO} (la franja dice que los datos son ficticios y nada se envía ni se
 * cobra; el perfil {@code demo} la exige).
 */
@ConfigurationProperties("cuentasclaras.entorno")
public record PropiedadesEntorno(@DefaultValue("") String nombre) {

	public static final String PILOTO = "PILOTO";

	/** Sprint 8, tanda 3 (sección 8.3): el entorno de demostración. */
	public static final String DEMO = "DEMO";

	/** El texto de la franja de la demo (sección 8.3, punto 4). */
	public static final String FRANJA_DEMO = "DEMO · Datos ficticios. Aquí nada envía mensajes ni cobra dinero. Se borra "
			+ "cada noche. No ingreses datos reales.";

	public PropiedadesEntorno {
		nombre = nombre == null ? "" : nombre.strip();
		if (nombre.length() > 20 || !nombre.matches("[A-Za-zÁÉÍÓÚÑáéíóúñ ]*")) {
			throw new IllegalArgumentException("cuentasclaras.entorno.nombre: hasta 20 letras (por ejemplo, PILOTO)");
		}
	}

	public boolean piloto() {
		return PILOTO.equalsIgnoreCase(nombre);
	}

	public boolean demo() {
		return DEMO.equalsIgnoreCase(nombre);
	}

	/** El texto de la franja global o {@code null} si no hay franja (producción). */
	public String franja() {
		if (nombre.isEmpty()) {
			return null;
		}
		if (demo()) {
			return FRANJA_DEMO;
		}
		return piloto() ? "PILOTO · Entorno de prueba: los pagos en línea son SIMULADOS, no son dinero real y no cambian "
				+ "las cuotas." : nombre.toUpperCase(java.util.Locale.ROOT) + " · Entorno de prueba";
	}
}
