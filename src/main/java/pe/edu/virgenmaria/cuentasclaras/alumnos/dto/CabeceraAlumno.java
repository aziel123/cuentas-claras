package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;

/** Lo que identifica al alumno en la cabecera de su ficha (y, en la tanda 3, de su cronograma). */
public record CabeceraAlumno(Long id, String nombreCompleto, String documento, EstadoAlumno estado,
		String estadoEtiqueta, Long familiaId, String familia, String seccionActual) {

	public boolean activo() {
		return estado == EstadoAlumno.ACTIVO;
	}
}
