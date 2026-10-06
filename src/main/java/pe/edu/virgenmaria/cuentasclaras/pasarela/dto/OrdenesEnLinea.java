package pe.edu.virgenmaria.cuentasclaras.pasarela.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Pagos en línea para Promotoría, Dirección y Administración: por revisar (primero), los de hoy y los vencidos. */
public record OrdenesEnLinea(LocalDate hoy, List<Fila> porRevisar, List<Fila> hoyPagadas, List<Fila> enCurso,
		List<Fila> vencidas, BigDecimal totalHoy, boolean simulada, boolean pasarelaActiva) {

	public record Fila(Long id, String referencia, String familia, BigDecimal monto, String estado, String etiqueta,
			String variante, String medio, String operacion, LocalDateTime creadaEn, String motivo, boolean critica,
			boolean simulada, String comprobante, String solicitudPendiente) {
	}
}
