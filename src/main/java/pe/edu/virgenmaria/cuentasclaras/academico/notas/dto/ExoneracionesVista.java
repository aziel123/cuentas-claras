package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

import java.util.List;

/**
 * Sprint 10, tanda 2: la pantalla «Exoneraciones» de Dirección para una sección: sus alumnos (solo nombres), las áreas
 * exonerables del nivel y las exoneraciones ya registradas. {@code periodoAbierto}: desde cuál regiría una nueva (A3), o
 * {@code null} si no hay un periodo abierto.
 */
public record ExoneracionesVista(List<Opcion> secciones, Long seccionId, String seccion, List<Opcion> alumnos,
		List<Opcion> areas, List<Registrada> registradas, String periodoAbierto) {

	public record Opcion(Long id, String nombre) {
	}

	public record Registrada(String alumno, String area, String desde, String documento, String autorizadoPor) {
	}
}
