package pe.edu.virgenmaria.cuentasclaras.privacidad.dto;

import java.time.LocalDate;

/** Filtro de /auditoria/accesos: una persona (opcional) y un rango de fechas (por defecto, los últimos 7 días). */
public record FiltroAccesos(Long usuarioId, LocalDate desde, LocalDate hasta) {
}
