package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Una línea de recaudación por revisar, con lo necesario para pedir aplicarla (a cuotas de su familia o de la familia
 * del código correcto) o devolverla, y para ejecutar la devolución aprobada.
 *
 * @param contacto celulares de la familia del código (para llamar antes de decidir)
 */
public record LineaExcepcionVista(Long id, Long loteId, String loteEstado, int numero, LocalDate fecha, String codigo,
		String alumno, String familia, BigDecimal monto, String moneda, String operacion, String estado, String etiqueta,
		String variante, String motivo, String detalle, boolean critica, String contacto, Long familiaDestinoId,
		String familiaDestino, String codigoDestino, List<CuotaDestino> cuotasDestino, List<String> solicitudes,
		boolean puedePedir, boolean puedeAplicar, boolean devolucionPorEjecutar, String devolucion, String comprobante,
		String devolucionDestino) {
}
