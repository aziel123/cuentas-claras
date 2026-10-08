package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import java.time.LocalDate;

/**
 * Correcciones del sprint 5 (S5-M3): alguien propuso un día no laborable del colegio. La mensajería avisa a Promotoría
 * en la misma transacción; el día no cuenta hasta que lo apruebe otra persona.
 */
public record FeriadoPropuesto(Long feriadoId, LocalDate fecha, String descripcion, String propuestoPor) {
}
