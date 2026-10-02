package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Verificación bancaria: los pagos digitales y los depósitos que Administración aún no comparó con el banco (los más
 * antiguos primero; {@code atrasado}: llevan más días que el límite configurado) y las últimas verificaciones.
 * Promotoría la ve; solo Administración verifica ({@code puedeVerificar}).
 */
public record VistaConciliacion(LocalDate hoy, boolean puedeVerificar, int diasLimite, List<PagoPorVerificar> pagos,
		List<DepositoPorVerificar> depositos, List<Verificado> recientes) {

	public record PagoPorVerificar(Long id, LocalDate fecha, String medio, String operacion, BigDecimal total,
			String familia, String cajera, String comprobante, long dias, boolean atrasado) {
	}

	public record DepositoPorVerificar(Long id, LocalDate fechaCaja, LocalDate fechaDeposito, String cuenta,
			String operacion, BigDecimal monto, BigDecimal esperado, boolean distinto, String explicacion, String cajera,
			long dias, boolean atrasado) {
	}

	public record Verificado(String que, String resultado, String variante, String nota, String por,
			LocalDateTime en) {
	}
}
