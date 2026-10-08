package pe.edu.virgenmaria.cuentasclaras.comun.error;

/**
 * Una regla del negocio impide la operación. El mensaje está en español y es apto
 * para mostrarse al usuario (sin detalles técnicos ni datos sensibles).
 */
public class ReglaNegocioException extends RuntimeException {

	public ReglaNegocioException(String mensaje) {
		super(mensaje);
	}
}
