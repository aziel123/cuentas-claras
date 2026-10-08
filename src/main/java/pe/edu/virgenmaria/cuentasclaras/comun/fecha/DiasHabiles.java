package pe.edu.virgenmaria.cuentasclaras.comun.fecha;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Qué días son hábiles (sprint 5, tanda 3): de lunes a viernes, sin feriados. Quien decide qué es feriado es la
 * implementación:
 * <ul>
 *   <li>{@link CalendarioHabil} (bean): los 16 feriados nacionales más los días no laborables que registró el colegio
 *       actual. Es la que usa todo el código de producción.</li>
 *   <li>{@link #NACIONALES}: solo los nacionales (procesos sin colegio y pruebas puras).</li>
 *   <li>{@link #LUNES_A_VIERNES}: sin feriados (el comportamiento anterior; solo para pruebas puras de bordes).</li>
 * </ul>
 */
@FunctionalInterface
public interface DiasHabiles {

	/** Sin feriados: solo descarta sábados y domingos. */
	DiasHabiles LUNES_A_VIERNES = fecha -> false;

	/** Los 16 feriados nacionales de ley ({@link FeriadosNacionales}). */
	DiasHabiles NACIONALES = FeriadosNacionales::es;

	/** Hasta cuántos días hábiles cuenta {@link #habilesEntre} (las alertas no miran más atrás). */
	int MAXIMO_CONTADO = 60;

	/** Si la fecha es feriado (nacional o, según la implementación, del colegio). */
	boolean esFeriado(LocalDate fecha);

	/** Lunes a viernes y no feriado. */
	default boolean esHabil(LocalDate fecha) {
		Objects.requireNonNull(fecha, "fecha");
		return fecha.getDayOfWeek() != DayOfWeek.SATURDAY && fecha.getDayOfWeek() != DayOfWeek.SUNDAY
				&& !esFeriado(fecha);
	}

	/** El día hábil siguiente (nunca la misma fecha). */
	default LocalDate siguienteDiaHabil(LocalDate fecha) {
		LocalDate siguiente = fecha.plusDays(1);
		while (!esHabil(siguiente)) {
			siguiente = siguiente.plusDays(1);
		}
		return siguiente;
	}

	/** El día hábil anterior (nunca la misma fecha). */
	default LocalDate anteriorDiaHabil(LocalDate fecha) {
		LocalDate anterior = fecha.minusDays(1);
		while (!esHabil(anterior)) {
			anterior = anterior.minusDays(1);
		}
		return anterior;
	}

	/** {@code dias} días hábiles después de la fecha (o antes, si es negativo). 0 devuelve la misma fecha. */
	default LocalDate sumarHabiles(LocalDate fecha, int dias) {
		LocalDate resultado = fecha;
		for (int i = 0; i < Math.abs(dias); i++) {
			resultado = dias > 0 ? siguienteDiaHabil(resultado) : anteriorDiaHabil(resultado);
		}
		return resultado;
	}

	/**
	 * Días hábiles entre dos fechas, sin contar la primera: 0 si es el mismo día o si entre ellas solo hay un fin de semana
	 * o un feriado. Cuenta hasta {@link #MAXIMO_CONTADO} + 1.
	 */
	default int habilesEntre(LocalDate a, LocalDate b) {
		LocalDate desde = a.isBefore(b) ? a : b;
		LocalDate hasta = a.isBefore(b) ? b : a;
		int dias = 0;
		for (LocalDate d = desde.plusDays(1); !d.isAfter(hasta); d = d.plusDays(1)) {
			if (esHabil(d)) {
				dias++;
			}
			if (dias > MAXIMO_CONTADO) {
				break;
			}
		}
		return dias;
	}

	/**
	 * Día en que puede salir un mensaje a una familia (recordatorios, sección 10.4): de lunes a SÁBADO y nunca en domingo
	 * ni feriado.
	 */
	default boolean admiteMensajes(LocalDate fecha) {
		return fecha.getDayOfWeek() != DayOfWeek.SUNDAY && !esFeriado(fecha);
	}

	/** El día de mensajes más cercano en o antes de la fecha (un recordatorio que cae en domingo sale el sábado). */
	default LocalDate diaDeMensajesEnOAntes(LocalDate fecha) {
		LocalDate dia = fecha;
		while (!admiteMensajes(dia)) {
			dia = dia.minusDays(1);
		}
		return dia;
	}

	/** El día de mensajes más cercano en o después de la fecha. */
	default LocalDate diaDeMensajesEnODespues(LocalDate fecha) {
		LocalDate dia = fecha;
		while (!admiteMensajes(dia)) {
			dia = dia.plusDays(1);
		}
		return dia;
	}
}
