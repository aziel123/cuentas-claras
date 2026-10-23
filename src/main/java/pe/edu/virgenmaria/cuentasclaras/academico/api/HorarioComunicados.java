package pe.edu.virgenmaria.cuentasclaras.academico.api;

import java.time.LocalTime;

/**
 * Sprint 9, tanda 3 (sección 3.10, decisiones 158 y 159): la hora del resumen diario de comunicados y el tope mensual de
 * resúmenes por WhatsApp de cada familia, que Dirección configura en «Horas de la asistencia». Lo lee {@code comunicacion}.
 */
public interface HorarioComunicados {

	LocalTime horaResumen();

	int topeResumenesMes();
}
