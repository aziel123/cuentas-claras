package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

/**
 * Puerto del correo de respaldo. Implementaciones: {@code CorreoSmtp} (real, apagada por defecto) y
 * {@code CorreoSimulado} (solo dev, test y piloto).
 */
public interface ProveedorCorreo {

	/** Envía un correo de texto. Nunca lanza: devuelve el resultado. */
	ResultadoEnvio enviar(String destino, String asunto, String cuerpo);

	/**
	 * Sprint 8, tanda 1 (sección 3.7): lo mismo, con el nombre visible del remitente («{colegio} vía {marca}»). La
	 * dirección sigue siendo la del remitente configurado; el proveedor que no distingue nombres lo ignora.
	 */
	default ResultadoEnvio enviar(String remitenteNombre, String destino, String asunto, String cuerpo) {
		return enviar(destino, asunto, cuerpo);
	}
}
