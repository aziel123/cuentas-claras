package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Pedir que una línea por revisar se aplique a cuotas de una familia (la suya o la del código correcto). */
public record AplicacionLineaRequest(
		@NotNull(message = "Elige la familia.") Long familiaId,
		@NotEmpty(message = "Elige al menos una cuota.") @Size(max = 30) List<Long> cuotaIds,
		@NotNull(message = "Escribe el motivo.") @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
