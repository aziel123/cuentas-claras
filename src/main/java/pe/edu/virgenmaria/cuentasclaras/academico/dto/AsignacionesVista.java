package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.util.List;

/**
 * Sprint 9, tanda 1: la pantalla de Docentes y secciones de un año. {@code docentes}: las cuentas activas con el rol
 * DOCENTE que se pueden asignar. {@code puedeProponer}: Dirección o Administración; {@code puedeTerminar}: Dirección o
 * Promotoría. {@code anioAnterior}: si hay asignaciones del año anterior que copiar.
 */
public record AsignacionesVista(List<OpcionAcademica> anios, Long anioId, Integer anio, boolean anioCerrado,
		List<SeccionConAsignaciones> secciones, List<OpcionAcademica> docentes, boolean puedeProponer,
		boolean puedeTerminar, boolean anioAnterior) {

	public AsignacionesVista {
		anios = List.copyOf(anios);
		secciones = List.copyOf(secciones);
		docentes = List.copyOf(docentes);
	}

	public List<OpcionAcademica> seccionesActivas() {
		return secciones.stream().filter(SeccionConAsignaciones::activa)
				.map(s -> new OpcionAcademica(s.seccionId(), s.seccion())).toList();
	}

	public boolean sinAnio() {
		return anioId == null;
	}
}
