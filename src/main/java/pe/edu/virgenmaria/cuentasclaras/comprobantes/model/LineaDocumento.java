package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Una línea del comprobante: el concepto (por ejemplo, la cuota) y su monto. */
public record LineaDocumento(String descripcion, BigDecimal monto) {

	public LineaDocumento {
		Objects.requireNonNull(descripcion, "descripcion");
		Objects.requireNonNull(monto, "monto");
	}
}
