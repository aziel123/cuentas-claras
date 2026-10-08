package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sprint 5: se aprobó un descuento o beca y ya se aplicó a sus cuotas. La mensajería lo escucha en la MISMA transacción
 * y avisa a todos los apoderados de la familia (G4: el descuento fantasma lo ve la familia).
 */
public record DescuentoAprobado(Long descuentoId, Long alumnoId, List<Long> cuotaIds, BigDecimal total,
		String aprobadoPor) {
}
