package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import java.time.LocalDateTime;

/**
 * Sprint 8, tanda 2 (sección 3.4): la Promotoría pasó el colegio a producción. La mensajería lo escucha en la MISMA
 * transacción y avisa a toda la Promotoría y al correo externo de control (si no puede avisar, el paso no se guarda).
 */
public record ColegioEnProduccion(Long colegioId, String promotoria, LocalDateTime momento) {
}
