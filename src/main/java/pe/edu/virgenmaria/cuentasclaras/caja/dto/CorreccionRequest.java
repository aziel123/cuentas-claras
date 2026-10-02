package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Pedido de corrección: el dinero de un pago se aplica a otras cuotas (de esta u otra familia). */
public record CorreccionRequest(
		@NotNull(message = "Elige la familia a la que pasa el pago.") Long familiaId,
		@NotEmpty(message = "Elige las cuotas a las que pasa el pago.")
		@Size(max = 24, message = "Elige como máximo 24 cuotas.") List<@NotNull Long> cuotaIds,
		@NotNull(message = "Indica el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener de 10 a 500 caracteres.") String motivo) {
}
