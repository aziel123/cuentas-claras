package pe.edu.virgenmaria.cuentasclaras.academico.dto.portal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.academico.model.MotivoJustificacion;

/**
 * Sprint 9, tanda 2 (sección 3.5, Ley 29733): el motivo es de una lista; la nota es opcional y corta, sin diagnósticos
 * ni datos de salud.
 */
public record JustificarRequest(
		@NotNull(message = "Elige el motivo de la falta.") MotivoJustificacion motivo,
		@Size(max = 200, message = "La nota puede tener hasta 200 caracteres.") String nota) {
}
