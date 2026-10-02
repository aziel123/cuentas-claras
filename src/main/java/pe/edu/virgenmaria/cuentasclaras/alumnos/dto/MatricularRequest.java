package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Matrícula en una sección. Sin fecha: hoy, o el inicio de clases si ya pasó. */
public record MatricularRequest(@NotNull(message = "Elige la sección.") Long seccionId,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
}
