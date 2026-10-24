package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Sprint 10, tanda 1: programar el periodo siguiente del año (el número y el nombre los pone el sistema). */
public record ProgramarPeriodoRequest(
		@NotNull(message = "Elige el año.") Long anioId,
		@NotNull(message = "Escribe el inicio del periodo.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
		@NotNull(message = "Escribe el fin del periodo.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {

	public static ProgramarPeriodoRequest vacio(Long anioId) {
		return new ProgramarPeriodoRequest(anioId, null, null);
	}
}
