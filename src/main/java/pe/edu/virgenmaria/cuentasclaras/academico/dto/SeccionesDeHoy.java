package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.time.LocalDate;
import java.util.List;

/** Sprint 9, tanda 2: las secciones del docente para tomar la asistencia de hoy. */
public record SeccionesDeHoy(LocalDate fecha, String fechaTexto, String motivoSinClases, List<OpcionSeccion> secciones) {
}
