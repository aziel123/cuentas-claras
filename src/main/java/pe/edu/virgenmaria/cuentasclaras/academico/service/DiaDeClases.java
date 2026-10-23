package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoAnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Sprint 9, tanda 2 (sección 3.4, H8): si una sección tiene clases ese día. De lunes a viernes, dentro de las fechas de
 * clases del año EN_CURSO, con la sección activa y sin feriado (los 16 nacionales, que solo conoce la aplicación, y los
 * días no laborables del colegio). En MySQL, trg_registro_asistencia_nace repite todo menos los feriados nacionales.
 */
@Component
class DiaDeClases {

	static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE"));

	private final CalendarioHabil calendario;

	DiaDeClases(CalendarioHabil calendario) {
		this.calendario = calendario;
	}

	/** Por qué la sección no tiene clases ese día, o vacío si las tiene. */
	Optional<String> sinClases(Seccion seccion, LocalDate fecha) {
		AnioEscolar anio = seccion.getAnioEscolar();
		if (!seccion.isActiva()) {
			return Optional.of("La sección está desactivada.");
		}
		if (anio.getEstado() != EstadoAnioEscolar.EN_CURSO) {
			return Optional.of("El año " + anio.getAnio() + " no está en curso.");
		}
		if (fecha.isBefore(anio.getInicioClases()) || fecha.isAfter(anio.getFinClases())) {
			return Optional.of("El " + texto(fecha) + " está fuera de las fechas de clases (" + anio.rangoClases() + ").");
		}
		if (fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
			return Optional.of("El " + texto(fecha) + " es fin de semana: no hay clases.");
		}
		if (calendario.esFeriado(fecha)) {
			return Optional.of("El " + texto(fecha) + " es feriado o día no laborable del colegio.");
		}
		return Optional.empty();
	}

	static String texto(LocalDate fecha) {
		return fecha.format(FECHA);
	}
}
