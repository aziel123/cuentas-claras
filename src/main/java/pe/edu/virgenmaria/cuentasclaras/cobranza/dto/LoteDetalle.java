package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle del lote con el control «total declarado frente a la suma de líneas» y lo que el usuario en sesión puede
 * hacer. {@code avisoConfirmacion} explica por qué no ve «Confirmar» si participó en el lote.
 * <p>
 * {@code mostrarTotales} es {@code false} para quien debe confirmarlo: escribe a ciegas el total del informe del
 * contador. {@code bloqueos}: cuotas futuras que la confirmación bloquearía (si hay alguna, no se confirma).
 * {@code version}: la que vio el usuario; al confirmar o devolver se comprueba que no cambió.
 */
public record LoteDetalle(Long id, Long anioId, int anio, LocalDate fechaCorte, String documentoReferencia,
		BigDecimal totalDeclarado, BigDecimal totalLineas, BigDecimal diferencia, boolean cuadra, String estado,
		String estadoEtiqueta, String estadoVariante, String creadoPor, LocalDateTime creadoEn, String enviadoPor,
		LocalDateTime enviadoEn, String confirmadoPor, LocalDateTime confirmadoEn, String devueltoPor,
		String motivoDevolucion, String descartadoPor, String motivoDescarte, List<LineaVista> lineas,
		boolean puedeEditar, boolean puedeEnviar, boolean puedeConfirmar, boolean puedeDevolver,
		boolean puedeDescartar, String avisoConfirmacion, boolean mostrarTotales, List<String> bloqueos,
		List<String> conceptosOtros, Long version) {
}
