package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Formulario de año escolar nuevo. */
public record CrearAnioEscolarRequest(
		@NotNull(message = "Escribe el año.")
		@Min(value = 2000, message = "El año debe estar entre 2000 y 2100.")
		@Max(value = 2100, message = "El año debe estar entre 2000 y 2100.") Integer anio,
		@NotNull(message = "Elige la fecha de inicio de clases.")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicioClases,
		@NotNull(message = "Elige la fecha de fin de clases.")
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate finClases,
		Boolean enCurso) {

	/** Casilla sin marcar (o ausente): no es el año en curso. Es {@code Boolean} para que el formulario siempre se pueda armar. */
	public boolean esEnCurso() {
		return Boolean.TRUE.equals(enCurso);
	}

	/** Propuesta para el año siguiente al último: clases del primer lunes de marzo a mediados de diciembre. */
	public static CrearAnioEscolarRequest propuesta(int anio) {
		LocalDate inicio = LocalDate.of(anio, 3, 1);
		while (inicio.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
			inicio = inicio.plusDays(1);
		}
		return new CrearAnioEscolarRequest(anio, inicio, LocalDate.of(anio, 12, 18), false);
	}
}
