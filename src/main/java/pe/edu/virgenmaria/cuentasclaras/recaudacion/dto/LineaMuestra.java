package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una línea al azar que quien confirma busca en el portal del banco (código, fecha, monto y operación). */
public record LineaMuestra(String codigo, LocalDate fecha, BigDecimal monto, String operacion) {
}
