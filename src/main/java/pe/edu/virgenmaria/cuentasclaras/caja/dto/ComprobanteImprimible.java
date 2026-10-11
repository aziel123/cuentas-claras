package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprobante para imprimir y entregar al apoderado. {@code emisor}: el colegio que emite, con su razón social, su RUC,
 * su dirección y su logo (H8, sprint 8). {@code simulado}: lleva la marca «COMPROBANTE SIMULADO · SIN VALOR TRIBUTARIO»
 * mientras no haya OSE. Una nota de crédito no tiene pago ({@code pagoId} null) y dice qué comprobante anula
 * ({@code anula}) y por qué ({@code motivoNota}). {@code atendio}: el nombre completo de quien lo emitió.
 * {@code firmaRecepcion}: la nota de crédito de una devolución trae espacio para la firma, el nombre y el DNI de quien
 * recibe el dinero (A2).
 */
public record ComprobanteImprimible(Long pagoId, EmisorVista emisor, String tipo, String numero, LocalDate fecha,
		LocalDateTime registradoEn, String receptorNombre, String receptorDocumento, List<LineaComprobanteVista> lineas,
		BigDecimal total, String moneda, String afectacion, String medio, BigDecimal recibido, BigDecimal vuelto,
		String numeroOperacion, String atendio, boolean simulado, String codigoHash, String anula, String motivoNota,
		boolean firmaRecepcion) {

	/** El nombre comercial del colegio emisor (compatibilidad con las vistas que solo muestran el nombre). */
	public String colegio() {
		return emisor == null ? null : emisor.nombre();
	}
}
