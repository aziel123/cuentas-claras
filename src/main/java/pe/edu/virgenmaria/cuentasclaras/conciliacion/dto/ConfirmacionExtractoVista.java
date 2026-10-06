package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que ve quien confirma los extractos de una cuenta: los pendientes con sus fechas, cuál toca confirmar (el más
 * antiguo: cada uno con su propio saldo), el día cuyo saldo debe escribir («el saldo que tu app del banco muestra al
 * cierre del 07/10») y la muestra fija de movimientos para buscarlos en la app, SIN monto ni tipo (S4-A1). NUNCA los
 * saldos ni los totales.
 *
 * @param extractoId   el extracto que toca confirmar (el pendiente más antiguo; su saldo final se escribe a ciegas)
 * @param participaste si quien mira subió el extracto que toca confirmar (o preparó la cuenta de quien lo subió)
 */
public record ConfirmacionExtractoVista(Long cuentaId, String cuenta, List<Pendiente> pendientes, Long extractoId,
		Long version, LocalDate desdeDel, LocalDate cierreDel, int intentosRestantes, boolean participaste,
		List<Muestra> muestra) {

	public boolean porConfirmar() {
		return extractoId != null;
	}

	public record Pendiente(Long id, int secuencia, LocalDate desde, LocalDate hasta, int movimientos, String subidoPor,
			LocalDateTime subidoEn) {
	}

	/** Un movimiento de la muestra: sin monto ni tipo (con ellos y el saldo anterior, el saldo «ciego» era una suma). */
	public record Muestra(LocalDate fecha, String descripcion, String operacion) {
	}
}
