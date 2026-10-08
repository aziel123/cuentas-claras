package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;

/** Alumno de una familia, con su sección del año en curso (si la tiene) y su responsable de pago. */
public record HermanoVista(Long id, String nombreCompleto, String seccion, EstadoAlumno estado, String estadoEtiqueta,
		String responsablePago) {
}
