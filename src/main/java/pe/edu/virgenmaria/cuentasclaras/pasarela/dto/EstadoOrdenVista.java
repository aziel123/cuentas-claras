package pe.edu.virgenmaria.cuentasclaras.pasarela.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El estado de un pago en línea para el apoderado. Nunca dice «pagado» por haber vuelto de la pasarela: solo cuando la
 * consulta a la pasarela lo confirmó y el pago quedó registrado con su boleta.
 */
public record EstadoOrdenVista(String referencia, String estado, String etiqueta, String variante, BigDecimal monto,
		String enlacePago, LocalDateTime venceEn, List<String> cuotas, String comprobante, Long comprobanteId,
		String medio, boolean enCurso, boolean simulada, boolean tardia, String mensaje) {

	/** Se puede seguir al pago (la orden está en curso y la pasarela ya dio el enlace). */
	public boolean puedePagar() {
		return enCurso && enlacePago != null;
	}
}
