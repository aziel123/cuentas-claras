package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;

import java.time.LocalDate;

/**
 * Sprint 9, tanda 1: proponer que una cuenta DOCENTE sea tutor, docente de área o auxiliar de una sección. Tanda 2 (A7):
 * {@code hasta} hace de la propuesta una COBERTURA de un docente ausente (hasta 5 días hábiles; nunca de tutor).
 */
public record ProponerAsignacionRequest(
		@NotNull(message = "Elige la sección.") Long seccionId,
		@NotNull(message = "Elige al docente.") Long docenteId,
		@NotNull(message = "Elige qué hará en la sección.") TipoAsignacion tipo,
		@Size(max = 120, message = "Las áreas pueden tener hasta 120 caracteres.") String areas,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

	/** Sprint 9, tanda 1: sin fecha de fin (no es una cobertura). */
	public ProponerAsignacionRequest(Long seccionId, Long docenteId, TipoAsignacion tipo, String areas, String motivo) {
		this(seccionId, docenteId, tipo, areas, motivo, null);
	}

	public static ProponerAsignacionRequest vacio() {
		return new ProponerAsignacionRequest(null, null, null, null, null, null);
	}
}
