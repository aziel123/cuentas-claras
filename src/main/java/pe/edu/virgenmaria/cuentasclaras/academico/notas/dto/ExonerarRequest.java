package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sprint 10, tanda 2 (E21): exonerar a un alumno de Religión o Educación Física. {@code documento}: el código del escrito
 * que presentó el padre (no se guarda el escrito). Rige desde el periodo ABIERTO (A3); no lleva el periodo: lo decide el
 * servicio.
 */
public record ExonerarRequest(
		@NotNull(message = "Elige al alumno.") Long matriculaId,
		@NotNull(message = "Elige el área.") Long areaId,
		@NotBlank(message = "Escribe el código del escrito del padre.")
		@Size(min = 3, max = 40, message = "El código del escrito va de 3 a 40 caracteres.") String documento,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 300, message = "El motivo va de 10 a 300 caracteres.") String motivo) {

	public static ExonerarRequest vacio() {
		return new ExonerarRequest(null, null, null, null);
	}
}
