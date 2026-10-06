package pe.edu.virgenmaria.cuentasclaras.pasarela.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una orden para el personal: lo pedido, lo que confirmó la pasarela, el motivo en lenguaje claro si quedó por revisar,
 * el contacto del apoderado (para llamarlo antes de aplicar o devolver) y lo que se puede hacer.
 */
public record DetalleOrden(Long id, String referencia, String familia, Long familiaId, String apoderado, String contacto,
		BigDecimal monto, String estado, String etiqueta, String variante, String proveedor, boolean simulada,
		LocalDateTime creadaEn, LocalDateTime venceEn, BigDecimal montoConfirmado, String medio, String operacion,
		String cargo, LocalDateTime confirmadoEn, boolean tardia, String motivo, String detalleRevision,
		List<String> cuotas, String comprobante, List<String> solicitudes, List<CuotaDestino> cuotasDeLaFamilia,
		boolean puedePedir, boolean devolucionPorEjecutar, String devolucion) {

	/** Una cuota por pagar a la que se podría aplicar el ingreso. */
	public record CuotaDestino(Long id, String alumno, String descripcion, BigDecimal saldo) {
	}
}
