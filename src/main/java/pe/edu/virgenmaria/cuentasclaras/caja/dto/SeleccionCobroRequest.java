package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;

import java.util.List;

/** Cuotas elegidas y medio de pago, para revisar el cobro. Sin monto: el total lo calcula el sistema. */
public record SeleccionCobroRequest(
		@NotEmpty(message = "Elige al menos una cuota.")
		@Size(max = 24, message = "Elige como máximo 24 cuotas por cobro.") List<@NotNull Long> cuotaIds,
		@NotNull(message = "Elige el medio de pago.") MedioPago medio) {
}
