package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Un extracto con sus movimientos y la pareja de cada uno. Mientras está por confirmar NO muestra saldos ni totales (los
 * escribe a ciegas quien confirma). Correcciones del sprint 4 (S4-A1): a quien puede confirmar (Promotoría o Dirección)
 * tampoco le muestra el monto, el tipo ni la pareja de cada movimiento ({@code montosOcultos}) mientras el extracto, u
 * otro de esos días, esté por confirmar: con ellos y el saldo anterior, el saldo «ciego» era una suma.
 */
public record DetalleExtracto(Long id, Long cuentaId, String cuenta, int secuencia, LocalDate desde, LocalDate hasta,
		String estado, String variante, boolean confirmado, BigDecimal saldoInicial, BigDecimal totalAbonos,
		BigDecimal totalCargos, BigDecimal saldoFinal, String archivo, String sha256, String subidoPor,
		LocalDateTime subidoEn, String confirmadoPor, LocalDateTime confirmadoEn, String motivoRechazo,
		boolean puedeDescartar, boolean montosOcultos, List<Movimiento> movimientos) {

	public record Movimiento(int numero, LocalDate fecha, String tipo, BigDecimal monto, BigDecimal saldo,
			String descripcion, String operacion, String pareja, String variante) {
	}
}
