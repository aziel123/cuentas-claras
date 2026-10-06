package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

/** El aviso no trae una firma o autenticación válida: se responde 401 y no se escribe nada en la bitácora. */
public class AvisoNoAutenticoException extends RuntimeException {

	public AvisoNoAutenticoException(String mensaje) {
		super(mensaje);
	}
}
