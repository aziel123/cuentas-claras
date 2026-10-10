package pe.edu.virgenmaria.cuentasclaras.panel.service;

import java.time.LocalDate;

/**
 * Una familia de la muestra no contestó por segunda vez (sprint 7, tanda 2): después del commit, {@code sistema.panel}
 * la reemplaza ({@code panel.proceso.ReemplazosLlamadas}).
 */
public record SegundoNoContesta(Long colegioId, LocalDate semana, Long familiaId) {
}
