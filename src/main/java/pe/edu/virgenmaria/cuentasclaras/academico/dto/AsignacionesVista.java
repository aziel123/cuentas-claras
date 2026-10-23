package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.util.List;

/**
 * Sprint 9, tanda 1: la pantalla de Docentes y secciones de un año. {@code docentes}: las cuentas activas con el rol
 * DOCENTE que se pueden asignar. {@code puedeProponer}: Dirección o Administración; {@code puedeTerminar}: Dirección o
 * Promotoría. {@code anioAnterior}: si hay asignaciones del año anterior que copiar.
 * <p>
 * Sprint 10, tanda 1: {@code areasDelAnio}, las áreas del catálogo de la versión del año por nivel (sin la transversal),
 * para la propuesta; {@code sinAreas}, las asignaciones vigentes sin áreas del catálogo (las del sprint 9: no califican hasta
 * volver a proponerlas); {@code evaluacionConfigurada}, si Dirección ya guardó la evaluación del año (sin ella no hay áreas).
 */
public record AsignacionesVista(List<OpcionAcademica> anios, Long anioId, Integer anio, boolean anioCerrado,
		List<SeccionConAsignaciones> secciones, List<OpcionAcademica> docentes, boolean puedeProponer,
		boolean puedeTerminar, boolean anioAnterior, List<AreasDelAnio> areasDelAnio, List<AsignacionSinAreas> sinAreas,
		boolean evaluacionConfigurada) {

	public AsignacionesVista {
		anios = List.copyOf(anios);
		secciones = List.copyOf(secciones);
		docentes = List.copyOf(docentes);
		areasDelAnio = List.copyOf(areasDelAnio);
		sinAreas = List.copyOf(sinAreas);
	}

	public List<OpcionAcademica> seccionesActivas() {
		return secciones.stream().filter(SeccionConAsignaciones::activa)
				.map(s -> new OpcionAcademica(s.seccionId(), s.seccion())).toList();
	}

	public boolean sinAnio() {
		return anioId == null;
	}
}
