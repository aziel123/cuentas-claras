package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import java.time.LocalDate;

/**
 * Sprint 5: la huella del día quedó guardada. La mensajería la envía en la misma transacción a cada usuario de
 * Promotoría activo y, si el DBA lo configuró, al correo externo del contador.
 *
 * @param huellaId       la huella guardada ({@code null} si NO se guardó porque la bitácora retrocedió: S5-M4)
 * @param verificacionOk si la cadena y las huellas guardadas de los últimos días coincidieron esta mañana
 * @param anterior       la huella anterior («evento 120, código 3fa1… del 04/10/2026» o «ninguna»): Promotoría compara
 *                       el mensaje de hoy con el de ayer (S5-M4)
 */
public record HuellaDelDia(Long colegioId, Long huellaId, LocalDate fecha, long secuencia, String codigo, int eventos,
		boolean verificacionOk, String anterior) {
}
