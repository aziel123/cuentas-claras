package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * El paso de seguridad antes de cobrar: total calculado por el sistema, cómo se aplica a cada cuota y a nombre de quién
 * sale el comprobante. {@code clave} identifica este cobro: si se envía dos veces, se registra una sola vez.
 */
public record RevisionCobro(UUID clave, Long familiaId, String familia, MedioPago medio, List<Long> cuotaIds,
		List<LineaRevision> lineas, BigDecimal total, List<OpcionReceptor> receptores, Long receptorPorDefecto,
		List<String> avisos, boolean pagoACuentaPermitido, BigDecimal pagoACuentaMinimo) {

	/** Una cuota elegida con el monto que se le aplica. */
	public record LineaRevision(String alumno, String descripcion, LocalDate vencimiento, BigDecimal monto) {
	}

	/** Un apoderado de la familia a cuyo nombre puede salir la boleta. */
	public record OpcionReceptor(Long apoderadoId, String nombre, String documento, boolean responsable) {
	}

	public boolean efectivo() {
		return medio == MedioPago.EFECTIVO;
	}
}
