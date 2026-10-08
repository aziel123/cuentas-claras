package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Deuda vencida a un día (sección 2, hallazgo 7): {@code monto − monto_pagado − monto_descuento} de las cuotas PENDIENTE o
 * PARCIAL con vencimiento ANTERIOR al día (el día del vencimiento aún se puede pagar). Las familias morosas se agrupan
 * por tramo según su cuota vencida más antigua.
 */
public record DeudaVencida(LocalDate al, BigDecimal monto, long familias, long cuotas, Tramos tramos) {
}
