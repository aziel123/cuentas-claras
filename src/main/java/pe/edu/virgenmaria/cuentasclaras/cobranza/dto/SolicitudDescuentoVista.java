package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Formulario de descuento: el alumno, sus hermanos matriculados y las cuotas a las que se puede aplicar. */
public record SolicitudDescuentoVista(Long alumnoId, String alumno, String documento, String familia,
		List<String> hermanosMatriculados, List<CuotaElegible> cuotas) {

	/** Una cuota PENDIENTE o PARCIAL del alumno. */
	public record CuotaElegible(Long id, String descripcion, LocalDate vencimiento, BigDecimal monto, BigDecimal descuento,
			BigDecimal pagado, BigDecimal saldo, String estadoEtiqueta, String estadoVariante) {
	}
}
