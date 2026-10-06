package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que ve quien confirma los extractos de una cuenta: los pendientes con sus fechas, el día cuyo saldo debe escribir
 * («el saldo que tu app del banco muestra al cierre del 07/10») y unos movimientos al azar para buscarlos en la app.
 * NUNCA los saldos ni los totales.
 *
 * @param ultimoId    el último extracto pendiente (su saldo final se escribe a ciegas)
 * @param participaste si quien mira subió alguno de los pendientes (o preparó la cuenta de quien lo subió)
 */
public record ConfirmacionExtractoVista(Long cuentaId, String cuenta, List<Pendiente> pendientes, Long ultimoId,
		Long version, LocalDate cierreDel, int intentosRestantes, boolean participaste, List<Muestra> muestra) {

	public boolean porConfirmar() {
		return ultimoId != null;
	}

	public record Pendiente(Long id, int secuencia, LocalDate desde, LocalDate hasta, int movimientos, String subidoPor,
			LocalDateTime subidoEn) {
	}

	public record Muestra(LocalDate fecha, String tipo, BigDecimal monto, String descripcion, String operacion) {
	}
}
