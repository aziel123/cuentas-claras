package pe.edu.virgenmaria.cuentasclaras.pasarela.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Las cuotas que el apoderado quiere pagar en línea. Solo ids: el monto lo calcula el servidor. */
public record SeleccionPagoRequest(
		@NotEmpty(message = "Elige al menos una cuota.") @Size(max = 30, message = "Elige hasta 30 cuotas.") List<Long> cuotaIds) {
}
