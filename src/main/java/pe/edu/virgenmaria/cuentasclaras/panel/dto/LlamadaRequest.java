package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.panel.model.LlamadaControl;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada;

/**
 * El resultado de una llamada de control (sprint 6, tanda 3). «No confirma» exige la nota (de 10 a 300 caracteres): lo
 * vuelve a exigir el servicio, porque el formulario se puede saltar.
 */
public record LlamadaRequest(
		@NotNull(message = "Elige qué respondió la familia") ResultadoLlamada resultado,
		@Size(max = LlamadaControl.MAX_NOTA, message = "La nota tiene como máximo 300 caracteres") String nota) {
}
