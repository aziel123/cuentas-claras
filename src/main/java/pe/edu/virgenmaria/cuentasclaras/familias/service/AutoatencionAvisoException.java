package pe.edu.virgenmaria.cuentasclaras.familias.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * S5-M2: alguien intentó atender el aviso de una familia sobre algo en lo que participó. El intento queda en la bitácora
 * aunque la operación no se haga (la transacción no se revierte por esta excepción).
 */
public class AutoatencionAvisoException extends ReglaNegocioException {

	public AutoatencionAvisoException(String mensaje) {
		super(mensaje);
	}
}
