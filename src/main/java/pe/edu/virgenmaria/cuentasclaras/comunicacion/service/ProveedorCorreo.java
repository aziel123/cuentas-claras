package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

/**
 * Puerto del correo de respaldo. Implementaciones: {@code CorreoSmtp} (real, apagada por defecto) y
 * {@code CorreoSimulado} (solo dev, test y piloto).
 */
public interface ProveedorCorreo {

	/** Envía un correo de texto. Nunca lanza: devuelve el resultado. */
	ResultadoEnvio enviar(String destino, String asunto, String cuerpo);
}
