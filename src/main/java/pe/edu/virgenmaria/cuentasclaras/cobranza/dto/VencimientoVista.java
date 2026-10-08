package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.time.LocalDate;

/** Un vencimiento del plan: «31/03/2027 · marzo». */
public record VencimientoVista(LocalDate fecha, String mes) {
}
