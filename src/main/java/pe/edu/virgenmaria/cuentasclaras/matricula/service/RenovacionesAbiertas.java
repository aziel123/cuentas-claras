package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import java.util.List;

/** Se abrieron propuestas de renovación (en la misma transacción): la mensajería invita a cada familia. */
public record RenovacionesAbiertas(List<Long> renovacionIds) {
}
