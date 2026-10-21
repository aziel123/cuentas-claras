package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Sprint 9, tanda 2 (sección 3.4): la hoja de asistencia de una sección para el docente. {@code registrado}: ya se guardó
 * (se ve el resultado y se corrige marca por marca). {@code cierreCorrecciones}: la hora del segundo aviso, desde la que la
 * corrección del día va por solicitud (A2). {@code motivoSinClases}: por qué ese día no se registra (fin de semana,
 * feriado, fuera del año), o {@code null}. {@code solicitudId}: la del registro tardío aprobado, si es un día pasado.
 */
public record HojaDelDia(long seccionId, String seccion, LocalDate fecha, String fechaTexto, boolean registrado,
		boolean correccionAbierta, LocalTime cierreCorrecciones, String motivoSinClases, Long solicitudId,
		List<AlumnoEnHoja> alumnos, int presentes, int faltas, int tardes, int justificadas) {
}
