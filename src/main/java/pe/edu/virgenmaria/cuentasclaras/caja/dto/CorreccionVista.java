package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pantalla de corrección de un pago: el pago, la búsqueda de la familia destino y sus cuotas con el saldo que tendrían
 * después de anular el pago ({@code deEstePago}: el pago se aplicó a esa cuota y volvería a deberse).
 */
public record CorreccionVista(Long pagoId, String comprobante, BigDecimal total, String medio, LocalDate fecha,
		Long familiaOrigenId, String familiaOrigen, String texto, List<ResultadoBusqueda> resultados,
		Long familiaDestinoId, String familiaDestino, List<CuotaDestino> cuotas) {

	/** Una cuota de la familia destino a la que puede pasar el pago. */
	public record CuotaDestino(Long id, String alumno, String descripcion, LocalDate vencimiento, BigDecimal saldo,
			boolean deEstePago) {
	}

	public boolean otraFamilia() {
		return familiaDestinoId != null && !familiaDestinoId.equals(familiaOrigenId);
	}
}
