package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una cuota por pagar a la que Administración puede pedir aplicar un ingreso por revisar. */
public record CuotaDestino(Long id, String descripcion, String alumno, LocalDate vencimiento, BigDecimal saldo) {
}
