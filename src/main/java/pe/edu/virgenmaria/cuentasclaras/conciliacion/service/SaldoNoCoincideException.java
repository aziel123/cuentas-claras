package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/** El saldo escrito a ciegas no coincide con el del extracto: se audita (resaltado) y suma un intento. */
public class SaldoNoCoincideException extends ReglaNegocioException {

	public SaldoNoCoincideException(String mensaje) {
		super(mensaje);
	}
}
