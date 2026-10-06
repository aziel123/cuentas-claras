package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Una pareja que propondría (o propuso) la conciliación: el movimiento del banco, lo emparejado, la regla y los avisos
 * («operación distinta», «número parecido»). {@code enRojo}: número parecido (posible número inventado).
 */
public record PropuestaVista(LocalDate fecha, String movimiento, BigDecimal monto, String objeto, String regla,
		String variante, List<String> avisos, boolean enRojo) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public PropuestaVista {
		avisos = avisos == null ? List.of() : List.copyOf(avisos);
	}
}
