package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Un día no laborable extra (solo fechas futuras). */
public record FeriadoRequest(
		@NotNull(message = "Elige la fecha.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
		@NotNull(message = "Escribe el motivo del día no laborable.")
		@Size(min = 5, max = 80, message = "Describe el día en 5 a 80 caracteres.") String descripcion) {
}
