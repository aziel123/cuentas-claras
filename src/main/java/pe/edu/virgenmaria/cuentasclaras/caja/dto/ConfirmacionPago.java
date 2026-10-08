package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Lo que ve la cajera después de cobrar: comprobante, total, medio y vuelto a entregar. */
public record ConfirmacionPago(Long id, String comprobante, String tipoComprobante, String receptor, BigDecimal total,
		String medio, boolean efectivo, BigDecimal recibido, BigDecimal vuelto, String numeroOperacion, boolean aCuenta,
		boolean simulado, String estadoEnvio, String familia, List<LineaComprobanteVista> lineas,
		LocalDateTime registradoEn) {
}
