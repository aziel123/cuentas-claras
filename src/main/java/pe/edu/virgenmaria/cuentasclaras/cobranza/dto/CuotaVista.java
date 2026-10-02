package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoVisibleCuota;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una cuota en el cronograma. {@code origen}: «Plan Primaria 2027 v1» o «Saldo inicial · lote 3, confirmado…». */
public record CuotaVista(Long id, String descripcion, String tipo, LocalDate vencimiento, BigDecimal monto,
		BigDecimal pagado, BigDecimal saldo, EstadoVisibleCuota estado, String origen, boolean anulacionPendiente) {

	public String estadoEtiqueta() {
		return estado.etiqueta();
	}

	public String estadoVariante() {
		return estado.variante();
	}
}
