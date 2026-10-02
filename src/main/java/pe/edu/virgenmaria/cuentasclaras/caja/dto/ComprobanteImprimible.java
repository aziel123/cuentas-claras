package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprobante para imprimir y entregar al apoderado. {@code simulado}: lleva la marca «COMPROBANTE SIMULADO · SIN
 * VALOR TRIBUTARIO» mientras no haya OSE.
 */
public record ComprobanteImprimible(Long pagoId, String colegio, String tipo, String numero, LocalDate fecha,
		LocalDateTime registradoEn, String receptorNombre, String receptorDocumento, List<LineaComprobanteVista> lineas,
		BigDecimal total, String moneda, String afectacion, String medio, BigDecimal recibido, BigDecimal vuelto,
		String numeroOperacion, String cajero, boolean simulado, String codigoHash) {
}
