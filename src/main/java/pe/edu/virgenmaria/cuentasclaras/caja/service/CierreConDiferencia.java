package pe.edu.virgenmaria.cuentasclaras.caja.service;

import java.math.BigDecimal;

/**
 * Un cierre de caja con faltante o sobrante. Correcciones del sprint 6 (S6-M3): lo escucha el panel DESPUÉS de confirmarse
 * la transacción ({@code AFTER_COMMIT}) y avisa al celular de Promotoría con la clave {@code C:<id>} aunque el cierre se
 * apruebe antes de la siguiente pasada de avisos.
 *
 * @param diferencia contado − esperado (negativa si falta)
 */
public record CierreConDiferencia(long colegioId, Long cierreId, BigDecimal diferencia) {
}
