package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Detalle de una caja para Promotoría y Dirección: sus pagos, sus cierres (con el primer conteo y la explicación), el
 * depósito con su verificación y las devoluciones aprobadas después del cierre («Anulado después del cierre: −S/ X»).
 * {@code depositoTardio}: llegó al banco más de un día hábil después de la caja (M3, en rojo). Un cierre
 * {@code trasReapertura} no fue ciego (hallazgo 4 de QA).
 */
public record DetalleCaja(Long id, String cajera, LocalDate fecha, String estado, String estadoVariante,
		BigDecimal fondo, BigDecimal efectivoVigente, BigDecimal digitalVigente, List<PagoCaja> pagos,
		List<CierreDetalle> cierres, String deposito, String depositoVerificacion, List<String> posterioresAlCierre,
		boolean depositoTardio) {

	public record PagoCaja(Long id, LocalTime hora, String comprobante, String familia, String medio, String operacion,
			BigDecimal total, String estado, String estadoVariante, String verificacion) {
	}

	public record CierreDetalle(int numero, LocalDateTime en, BigDecimal esperado, BigDecimal primerConteo,
			BigDecimal contado, String diferencia, String diferenciaVariante, String explicacion, String denominaciones,
			String estado, String estadoVariante, String revisadoPor, String comentario, boolean trasReapertura) {
	}
}
