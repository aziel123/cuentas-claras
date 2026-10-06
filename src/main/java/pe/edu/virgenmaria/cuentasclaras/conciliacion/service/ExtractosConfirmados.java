package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import java.util.List;

/** Se confirmó a ciegas la cadena de extractos de una cuenta: el sistema concilia después del commit. */
public record ExtractosConfirmados(Long colegioId, Long cuentaId, List<Long> extractos) {
}
