package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import java.time.LocalDate;

/**
 * Una línea de la muestra fija que quien confirma busca en el portal del banco: código, fecha y operación. Sin el monto
 * (S4-A1: con los montos de la muestra se reconstruía el total que debe escribir a ciegas).
 */
public record LineaMuestra(String codigo, LocalDate fecha, String operacion) {
}
