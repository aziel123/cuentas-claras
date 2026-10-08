package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Alguien intentó aprobar o confirmar lo que él mismo creó, editó, envió o solicitó (quien cobra no aprueba).
 * Los servicios la lanzan con {@code noRollbackFor}: el intento queda en la bitácora como
 * {@code AUTOAPROBACION_RECHAZADA}.
 */
public class AutoaprobacionException extends ReglaNegocioException {

	public AutoaprobacionException(String mensaje) {
		super(mensaje);
	}
}
