package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.util.List;

/**
 * Sprint 9, tanda 1: la lista de una sección (docente asignado o Dirección). {@code tutor}: el nombre del tutor vigente o
 * {@code null}. {@code miRol}: qué es quien mira en la sección («Tutor(a)», «Docente de área: Matemática»), o «Dirección».
 */
public record ListaSeccion(Long seccionId, String seccion, int anio, String tutor, String miRol,
		List<AlumnoDeSeccion> alumnos) {

	public ListaSeccion {
		alumnos = List.copyOf(alumnos);
	}

	public int cantidad() {
		return alumnos.size();
	}
}
