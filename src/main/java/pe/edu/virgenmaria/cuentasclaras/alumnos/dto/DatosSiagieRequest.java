package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Sprint 9, tanda 3 (sección 3.11): el sexo (M o F) y el código de estudiante del SIAGIE (solo para quien no tiene DNI),
 * opcionales. Se guardan con el antes y el después en la bitácora.
 */
public record DatosSiagieRequest(
		@Pattern(regexp = "[MFmf]?", message = "El sexo es M o F.") String sexo,
		@Pattern(regexp = "[0-9A-Za-z]{0,20}", message = "El código de estudiante tiene hasta 20 letras o dígitos.") String codigoEstudiante) {
}
