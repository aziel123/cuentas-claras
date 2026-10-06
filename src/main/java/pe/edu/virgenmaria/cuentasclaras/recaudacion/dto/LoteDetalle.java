package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El lote con sus líneas. Mientras está por confirmar ({@code montosVisibles = false}) no muestra el total ni los montos
 * de las líneas: quien confirma debe escribir lo que ve en el banco, no lo que dice el archivo.
 */
public record LoteDetalle(Long id, Long version, String banco, String formato, String archivo, String sha256,
		LocalDate fechaProceso, LocalDate desde, LocalDate hasta, int lineas, BigDecimal total, String estado,
		String etiqueta, String variante, String cargadoPor, LocalDateTime cargadoEn, String confirmadoPor,
		LocalDateTime confirmadoEn, int intentosConfirmacion, String rechazadoPor, String motivoRechazo, int aplicadas,
		int excepciones, BigDecimal montoAplicado, BigDecimal montoExcepcion, boolean montosVisibles,
		boolean archivoDescargable, boolean puedeDescartar, List<LineaVista> detalle) {

	public boolean porConfirmar() {
		return "CARGADO".equals(estado);
	}
}
