package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import java.math.BigDecimal;

/**
 * Un cierre mensual en pantalla (pantalla 12). Mientras está ABIERTO, los totales calculados NO viajan a la vista
 * ({@code null}): quien cierra escribe a ciegas lo que dice el estado de cuenta oficial.
 *
 * @param puedeRegistrar ABIERTO y quien mira no subió ni confirmó extractos de ese mes
 */
public record CierreMensualVista(Long id, Long version, String cuenta, String periodo, String estado, String variante,
		boolean abierto, int intentosRestantes, boolean puedeRegistrar, String motivoNoPuede, BigDecimal totalAbonos,
		BigDecimal totalCargos, BigDecimal saldoFinal, String registradoPor) {
}
