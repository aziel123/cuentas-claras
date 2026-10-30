package pe.edu.virgenmaria.cuentasclaras.colegio.service;

import java.util.List;
import java.util.Objects;

/**
 * Sprint 11, tanda 2 (pendiente de la 1c, A1c-4): quien cambió la cuenta bancaria del colegio en los últimos 30 días
 * intentó confirmar la conciliación y se rechazó. Lo publica el colegio; al confirmarse su transacción, la plataforma avisa
 * a los operadores (en su propio colegio).
 *
 * @param parametros los de la plantilla {@code CONCILIACION_BLOQUEADA}: quién y qué intentó confirmar
 */
public record ConciliacionBloqueada(Long colegioId, List<String> parametros, String entidad, Long entidadId) {

	public ConciliacionBloqueada {
		Objects.requireNonNull(colegioId, "colegioId");
		parametros = List.copyOf(parametros);
	}
}
