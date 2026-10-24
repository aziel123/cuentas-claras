package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 10, tanda 1: la pantalla «Periodos» de un año. {@code configurada}: si el año ya tiene su configuración de
 * evaluación (sin ella no se programan periodos); {@code siguienteNumero} y {@code siguienteNombre}: el que se puede
 * programar ahora (los periodos van en orden), o {@code null} si ya están todos.
 */
public record PeriodosVista(List<OpcionAnio> anios, Long anioId, Integer anio, boolean anioCerrado, LocalDate inicioClases,
		LocalDate finClases, boolean configurada, String tipoPeriodo, int numeroPeriodos, int diasCierreAnticipado,
		List<PeriodoVista> periodos, Integer siguienteNumero, String siguienteNombre, LocalDate hoy) {
}
