package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.SeccionOpcion;

import java.time.LocalDate;
import java.util.List;

/** Matrícula de un año. {@code otrasSecciones}: secciones activas del mismo año a las que se puede mover. */
public record MatriculaVista(Long id, Long anioId, int anio, String seccion, LocalDate fechaMatricula,
		EstadoMatricula estado, String estadoEtiqueta, LocalDate retiradaEn, List<SeccionOpcion> otrasSecciones) {

	public boolean activa() {
		return estado == EstadoMatricula.ACTIVA;
	}
}
