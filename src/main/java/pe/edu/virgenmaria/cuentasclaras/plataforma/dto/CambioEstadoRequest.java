package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.MotivoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;

/** Sprint 11, tanda 1: la propuesta de un cambio de estado del colegio (o de su facultad), de un operador. */
public record CambioEstadoRequest(
		@NotNull(message = "Elige qué cambio propones.") TipoCambioEstado tipo,
		@NotNull(message = "Elige el motivo.") MotivoCambioEstado motivoTipo,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 20, max = 500, message = "El motivo va de 20 a 500 caracteres.") String motivo,
		@Size(max = 120, message = "La referencia del aviso va hasta 120 caracteres.") String referenciaAviso) {

	public static CambioEstadoRequest vacio() {
		return new CambioEstadoRequest(null, null, "", "");
	}
}
