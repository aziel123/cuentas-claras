package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

/** Sprint 9, tanda 3: registrar o corregir el código modular de un nivel (7 dígitos) y su anexo (1 dígito). */
public record CodigoModularRequest(
		@NotNull(message = "Elige el nivel.") Nivel nivel,
		@NotNull(message = "Escribe el código modular.") @Pattern(regexp = "[0-9]{7}", message = "El código modular tiene 7 dígitos.") String codigo,
		@Pattern(regexp = "[0-9]?", message = "El anexo es un dígito.") String anexo) {
}
