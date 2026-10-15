package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.util.List;

/** Sprint 9, tanda 1: una sección del año con sus asignaciones (vigentes, por aprobar y su historia). */
public record SeccionConAsignaciones(Long seccionId, String seccion, boolean activa, List<AsignacionVista> asignaciones) {

	public SeccionConAsignaciones {
		asignaciones = List.copyOf(asignaciones);
	}

	public boolean sinTutor() {
		return asignaciones.stream().noneMatch(a -> a.vigente() && "Tutor(a)".equals(a.tipo()));
	}
}
