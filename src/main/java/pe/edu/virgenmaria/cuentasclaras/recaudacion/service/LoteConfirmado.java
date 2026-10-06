package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

/** Otra persona confirmó a ciegas el total del lote: después del commit, el sistema aplica sus pagos. */
public record LoteConfirmado(Long loteId, Long colegioId) {
}
