package pe.edu.virgenmaria.cuentasclaras.comunicados.api;

import java.util.Collection;
import java.util.Map;

/**
 * Sprint 9, tanda 3 (sección 3.10): si los mensajes que avisaron un comunicado llegaron o se leyeron (para las
 * estadísticas). Lo implementa {@code comunicacion}, que depende de {@code comunicados.api} y no al revés (sin ciclos).
 */
public interface EstadoDeAvisos {

	/** El estado de entrega de un aviso: {@code entregado} (llegó o el servidor de correo lo aceptó) y {@code leido}. */
	record Entrega(boolean entregado, boolean leido) {
	}

	Map<Long, Entrega> de(Collection<Long> mensajes);
}
