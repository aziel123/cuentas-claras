package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

/** Sección con su conteo de alumnos matriculados (matrículas activas). */
public record SeccionVista(Long id, Grado grado, String etiqueta, String nombre, boolean activa, long matriculados) {
}
