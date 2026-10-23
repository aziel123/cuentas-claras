package pe.edu.virgenmaria.cuentasclaras.academico.dto.portal;

import java.util.List;

/** Sprint 9, tanda 2: el mes de asistencia de un hijo (lo que ve la familia en /familia/asistencia). */
public record MesDeHijo(long alumnoId, String nombre, int faltas, int tardes, int justificadas, List<DiaDelMes> dias) {
}
