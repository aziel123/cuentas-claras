package pe.edu.virgenmaria.cuentasclaras.pasarela.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Revisión antes de pagar: el total calculado por el servidor, las cuotas y a nombre de quién sale el comprobante. */
public record RevisionPagoEnLinea(UUID clave, List<Long> cuotaIds, List<Linea> lineas, BigDecimal total, String receptor,
		String documentoEnmascarado, boolean puedeFactura, String razonSocial, boolean simulada) {

	public record Linea(String alumno, String descripcion, LocalDate vencimiento, BigDecimal monto) {
	}
}
