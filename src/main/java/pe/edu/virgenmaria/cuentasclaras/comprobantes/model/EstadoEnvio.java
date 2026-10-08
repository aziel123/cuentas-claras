package pe.edu.virgenmaria.cuentasclaras.comprobantes.model;

/**
 * Estado del envío al OSE (outbox, sprint 4).
 * <ul>
 *   <li>PENDIENTE: aún no se envió o el envío falló (se reintenta con espera creciente).</li>
 *   <li>ENVIADO: el OSE lo recibió y falta su respuesta definitiva: se CONSULTA, no se reenvía.</li>
 *   <li>ACEPTADO, OBSERVADO (válido, con observaciones) y RECHAZADO: definitivos; ya no cambian (trigger en MySQL). Un
 *       RECHAZADO se reemite con un número nuevo.</li>
 * </ul>
 */
public enum EstadoEnvio {

	PENDIENTE, ENVIADO, ACEPTADO, OBSERVADO, RECHAZADO;

	/** Resultado definitivo del OSE: no cambia. */
	public boolean definitivo() {
		return this == ACEPTADO || this == OBSERVADO || this == RECHAZADO;
	}

	/** Válido ante SUNAT (ACEPTADO u OBSERVADO). */
	public boolean valido() {
		return this == ACEPTADO || this == OBSERVADO;
	}

	public String etiqueta() {
		return switch (this) {
			case PENDIENTE -> "Pendiente de envío";
			case ENVIADO -> "Enviado, esperando respuesta";
			case ACEPTADO -> "Aceptado";
			case OBSERVADO -> "Aceptado con observaciones";
			case RECHAZADO -> "Rechazado";
		};
	}
}
