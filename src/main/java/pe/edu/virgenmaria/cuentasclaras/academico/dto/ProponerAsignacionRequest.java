package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 9, tanda 1: proponer que una cuenta DOCENTE sea tutor, docente de área o auxiliar de una sección. Tanda 2 (A7):
 * {@code hasta} hace de la propuesta una COBERTURA de un docente ausente (hasta 5 días hábiles; nunca de tutor).
 * <p>
 * Sprint 10, tanda 1 (sección 3.4, A2): {@code areaIds}, las áreas del catálogo que calificará (del nivel de la sección y
 * de la versión del año; sin la transversal, que es del tutor); {@code todasLasAreas}, para el tutor de Inicial o Primaria,
 * todas las del grado. Viajan en la propuesta y se aprueban con ella. {@code areas} (texto libre del sprint 9) queda solo
 * para las propuestas sin catálogo.
 */
public record ProponerAsignacionRequest(
		@NotNull(message = "Elige la sección.") Long seccionId,
		@NotNull(message = "Elige al docente.") Long docenteId,
		@NotNull(message = "Elige qué hará en la sección.") TipoAsignacion tipo,
		@Size(max = 120, message = "Las áreas pueden tener hasta 120 caracteres.") String areas,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
		@Size(max = 15, message = "Elige hasta 15 áreas.") List<Long> areaIds,
		Boolean todasLasAreas) {

	public ProponerAsignacionRequest {
		areaIds = areaIds == null ? List.of() : areaIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
		todasLasAreas = Boolean.TRUE.equals(todasLasAreas);
	}

	/** Sprint 9, tanda 1: sin fecha de fin (no es una cobertura) ni áreas del catálogo. */
	public ProponerAsignacionRequest(Long seccionId, Long docenteId, TipoAsignacion tipo, String areas, String motivo) {
		this(seccionId, docenteId, tipo, areas, motivo, null, List.of(), false);
	}

	/** Sprint 9, tanda 2: una cobertura (o sin fecha), sin áreas del catálogo. */
	public ProponerAsignacionRequest(Long seccionId, Long docenteId, TipoAsignacion tipo, String areas, String motivo,
			LocalDate hasta) {
		this(seccionId, docenteId, tipo, areas, motivo, hasta, List.of(), false);
	}

	/** Sprint 10, tanda 1: con las áreas del catálogo que calificará. */
	public static ProponerAsignacionRequest conAreas(Long seccionId, Long docenteId, TipoAsignacion tipo, String motivo,
			List<Long> areaIds) {
		return new ProponerAsignacionRequest(seccionId, docenteId, tipo, null, motivo, null, areaIds, false);
	}

	public static ProponerAsignacionRequest vacio() {
		return new ProponerAsignacionRequest(null, null, null, null, null, null, List.of(), false);
	}
}
