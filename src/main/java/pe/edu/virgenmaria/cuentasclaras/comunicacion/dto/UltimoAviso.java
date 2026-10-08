package pe.edu.virgenmaria.cuentasclaras.comunicacion.dto;

import java.time.LocalDateTime;

/** El último aviso de cobranza entregado a una familia (sprint 6): solo el tipo y cuándo se entregó. */
public record UltimoAviso(String tipo, LocalDateTime entregadoEn) {
}
