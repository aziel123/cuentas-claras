package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion;

/** Lo que Administración encontró en el banco. «No aparece» exige una nota. */
public record VerificacionRequest(
		@NotNull(message = "Indica si lo encontraste en el banco.") ResultadoVerificacion resultado,
		@Size(max = 500, message = "La nota tiene hasta 500 caracteres.") String nota) {
}
