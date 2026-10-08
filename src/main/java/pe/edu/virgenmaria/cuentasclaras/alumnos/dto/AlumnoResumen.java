package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;

/**
 * Fila de la lista de alumnos. {@code seccion}: la de su matrícula activa en {@code anio} (el año filtrado o el año
 * en curso); {@code null} si no está matriculado en ese año.
 */
public record AlumnoResumen(Long id, String nombreCompleto, String documento, Integer anio, String seccion,
		EstadoAlumno estado, String estadoEtiqueta) {
}
