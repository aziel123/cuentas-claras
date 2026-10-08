package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;

import java.util.List;

/**
 * Puerto de WhatsApp. Implementaciones: {@code WhatsAppCloudApi} (real, apagada por defecto) y {@code WhatsAppSimulado}
 * (solo dev, test y piloto). Ninguna se activa si falta la propiedad ({@code @ConditionalOnProperty} sin
 * {@code matchIfMissing}).
 */
public interface ProveedorWhatsApp {

	/**
	 * Envía la plantilla aprobada con sus parámetros. {@code sufijoBoton} completa la URL del botón (la ruta del enlace de
	 * activación, o {@code null} para el botón fijo del portal). Nunca lanza: devuelve el resultado.
	 */
	ResultadoEnvio enviar(PlantillaMensaje plantilla, String destino, List<String> parametros, String sufijoBoton);
}
