package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Verificación bancaria A CIEGAS (correcciones del sprint 3, C1): los pagos digitales y los depósitos por verificar NO
 * muestran el número de operación ni el monto registrados; Administración escribe lo que ve en el banco y el sistema
 * compara. {@code parecido}: otro pago o depósito con un número de operación parecido (mismo o a un carácter), para
 * revisar con cuidado. {@code atrasado}: lleva más tiempo que el límite; {@code critico}: pasó el día hábil siguiente.
 * Promotoría la ve; solo Administración verifica y registra reembolsos ({@code puedeVerificar}).
 */
public record VistaConciliacion(LocalDate hoy, boolean puedeVerificar, int diasLimite, List<PagoPorVerificar> pagos,
		List<DepositoPorVerificar> depositos, List<DevolucionPorReembolsar> devoluciones, List<Verificado> recientes) {

	public record PagoPorVerificar(Long id, LocalDate fecha, String medio, String familia, String cajera,
			String comprobante, long dias, boolean atrasado, boolean critico, String parecido) {
	}

	public record DepositoPorVerificar(Long id, LocalDate fechaCaja, String cuenta, String cajera, long dias,
			boolean atrasado, boolean tardio, String parecido) {
	}

	/** Devolución aprobada que espera su reembolso (por el mismo medio del pago). */
	public record DevolucionPorReembolsar(Long anulacionId, String comprobante, String notaCredito, String medio,
			boolean efectivo, BigDecimal monto, String familia, String cajera, LocalDate aprobadaEl) {
	}

	public record Verificado(String que, String resultado, String variante, String nota, String por,
			LocalDateTime en) {
	}
}
