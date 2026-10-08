package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Las cajas de un día para Promotoría y Dirección, con su cierre (y diferencia) y su depósito. */
public record CajasDelDia(LocalDate fecha, LocalDate anterior, LocalDate siguiente, BigDecimal totalEfectivo,
		BigDecimal totalDigital, List<CajaDelDia> cajas) {

	public record CajaDelDia(Long id, String cajera, String estado, String estadoVariante, int pagos,
			BigDecimal efectivo, BigDecimal digital, String cierre, String cierreVariante, String diferencia,
			String diferenciaVariante, String deposito) {
	}
}
