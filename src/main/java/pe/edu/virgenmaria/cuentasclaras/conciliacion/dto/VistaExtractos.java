package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Las cuentas del colegio y los extractos recientes (el saldo final solo se muestra cuando ya está confirmado). */
public record VistaExtractos(List<CuentaVista> cuentas, List<Fila> extractos, boolean puedeSubir,
		boolean puedeConfirmar, boolean puedeRegistrarCuentas) {

	public record Fila(Long id, String cuenta, int secuencia, LocalDate desde, LocalDate hasta, int movimientos,
			String estado, String variante, BigDecimal saldoFinal, String subidoPor, LocalDateTime subidoEn,
			String confirmadoPor) {
	}
}
