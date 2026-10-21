package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.time.LocalDate;

/**
 * Rediseño (tablero de Dirección): la asistencia de un día en números, sin nombres de alumnos: cuántas marcas vigentes de
 * cada estado y en cuántas secciones se registró.
 */
public record AsistenciaDelDia(LocalDate fecha, long presentes, long tarde, long faltas, long justificadas,
		long secciones) {

	public long total() {
		return presentes + tarde + faltas + justificadas;
	}
}
