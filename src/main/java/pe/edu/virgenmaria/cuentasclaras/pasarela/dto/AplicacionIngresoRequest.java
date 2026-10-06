package pe.edu.virgenmaria.cuentasclaras.pasarela.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Pedir que un ingreso por revisar se aplique a otras cuotas (de la misma familia o de otra, con llamada a ambas). */
public record AplicacionIngresoRequest(
		@NotNull(message = "Elige la familia.") Long familiaId,
		@NotEmpty(message = "Elige al menos una cuota.") @Size(max = 30) List<Long> cuotaIds,
		@NotNull(message = "Escribe el motivo.") @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
