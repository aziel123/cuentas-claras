package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ModalidadDescuento;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoDescuento;

import java.math.BigDecimal;
import java.util.List;

/** Pedido de descuento o beca para cuotas de un alumno. Lo que se dejará de cobrar lo calcula el sistema. */
public record DescuentoRequest(
		@NotNull(message = "Elige al alumno.") Long alumnoId,
		@NotNull(message = "Elige el tipo de descuento.") TipoDescuento tipo,
		@NotNull(message = "Elige porcentaje o monto fijo.") ModalidadDescuento modalidad,
		@NotNull(message = "Indica el porcentaje o el monto.")
		@Digits(integer = 5, fraction = 2, message = "El valor tiene como máximo 2 decimales.") BigDecimal valor,
		@NotEmpty(message = "Elige al menos una cuota.")
		@Size(max = 24, message = "Elige como máximo 24 cuotas.") List<@NotNull Long> cuotaIds,
		@NotNull(message = "Indica el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener de 10 a 500 caracteres.") String motivo,
		@NotNull(message = "Indica el sustento.")
		@Size(min = 5, max = 200, message = "El sustento debe tener de 5 a 200 caracteres.") String sustento) {
}
