package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Sprint 10, tanda 1 (M7): las fechas nuevas de un periodo (en ABIERTO, el inicio no cambia y el fin solo se extiende). */
public record FechasPeriodoRequest(
		@NotNull(message = "Escribe el inicio del periodo.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
		@NotNull(message = "Escribe el fin del periodo.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
}
