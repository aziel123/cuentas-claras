package pe.edu.virgenmaria.cuentasclaras.caja.service;

import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Lo que pide la cajera al confirmar un cobro. No trae el total a cobrar: {@link LibroPagos} lo calcula y lo compara
 * con {@code totalVisto}.
 */
public record OrdenCobro(List<Long> cuotaIds, MedioPago medio, String numeroOperacion, BigDecimal recibido,
		BigDecimal montoACuenta, BigDecimal totalVisto, DatosComprobante comprobante, UUID clave) {
}
