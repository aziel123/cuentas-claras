package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import java.time.LocalDate;

/**
 * Sprint 5: la huella del día quedó guardada. La mensajería la envía en la misma transacción a cada usuario de
 * Promotoría activo y, si el DBA lo configuró, al correo externo del contador.
 *
 * @param verificacionOk si la cadena y las huellas guardadas de los últimos días coincidieron esta mañana
 */
public record HuellaDelDia(Long colegioId, Long huellaId, LocalDate fecha, long secuencia, String codigo, int eventos,
		boolean verificacionOk) {
}
