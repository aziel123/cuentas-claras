package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import java.math.BigDecimal;

/** Una línea de un comprobante para mostrar o imprimir. */
public record LineaComprobanteVista(String descripcion, BigDecimal monto) {
}
