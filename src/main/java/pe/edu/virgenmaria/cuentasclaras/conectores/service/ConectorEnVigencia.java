package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;

import java.time.LocalDateTime;

/**
 * Sprint 8, tanda 2 (sección 3.6, E12): un conector entró en vigencia (cambió adónde entra el dinero o quién emite las
 * boletas). La mensajería lo escucha en la MISMA transacción y avisa a toda la Promotoría y al correo externo de control:
 * si no puede avisar, el cambio no se guarda.
 *
 * @param detalle lo que identifica el cambio sin secretos ni números completos («cuenta que termina en 1234»)
 */
public record ConectorEnVigencia(Long colegioId, Long conectorId, TipoConector tipo, String proveedor, String detalle,
		String propuestoPor, String aprobadoPor, LocalDateTime momento) {
}
