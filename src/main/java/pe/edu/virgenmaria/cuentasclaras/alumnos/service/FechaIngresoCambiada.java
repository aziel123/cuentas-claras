package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import java.time.LocalDate;

/**
 * Se aprobó un ingreso tardío: la matrícula cobra desde {@code fecha}. Cobranza lo escucha (síncrono, en la misma
 * transacción) para anular las pensiones anteriores al ingreso.
 */
public record FechaIngresoCambiada(Long matriculaId, LocalDate fecha, String solicitante, String aprobador) {
}
