package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** Sprint 9, tanda 2: las horas y plazos de la asistencia del colegio (pantalla de Dirección). */
public record ConfiguracionVista(LocalTime horaAlerta, LocalTime horaAviso, LocalTime horaSegundoAviso,
		int diasJustificacion, int diasRegistroTardio, LocalTime horaResumenComunicados, int topeResumenesMes,
		String actualizadoPor, LocalDateTime actualizadoEn) {
}
