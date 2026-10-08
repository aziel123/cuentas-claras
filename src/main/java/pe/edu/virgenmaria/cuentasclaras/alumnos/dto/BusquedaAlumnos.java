package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;

/** Filtros de la lista de alumnos: nombre o documento, año, sección y estado (todos opcionales). */
public record BusquedaAlumnos(String texto, Long anioId, Long seccionId, EstadoAlumno estado) {

	public static BusquedaAlumnos todos() {
		return new BusquedaAlumnos(null, null, null, null);
	}

	public boolean conFiltros() {
		return texto != null && !texto.isBlank() || anioId != null || seccionId != null || estado != null;
	}
}
