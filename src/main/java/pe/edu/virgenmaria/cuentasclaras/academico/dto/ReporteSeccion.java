package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.util.List;

/**
 * Sprint 9, tanda 2 (sección 3.4): el reporte mensual de una sección (tutor y Dirección): días de clase registrados y,
 * por alumno, presentes, tardanzas, faltas, faltas justificadas y el porcentaje de inasistencia injustificada.
 */
public record ReporteSeccion(long seccionId, String seccion, String mes, int diasRegistrados, List<FilaReporte> filas) {

	/** Una fila del reporte (un alumno). */
	public record FilaReporte(int numero, long alumnoId, String alumno, int presentes, int tardes, int faltas,
			int justificadas, String inasistenciaInjustificada) {
	}
}
