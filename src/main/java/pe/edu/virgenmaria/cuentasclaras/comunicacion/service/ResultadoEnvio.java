package pe.edu.virgenmaria.cuentasclaras.comunicacion.service;

import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;

import java.util.Objects;

/**
 * Respuesta de un proveedor de mensajería: ACEPTADO (con su id), ERROR_REINTENTABLE (red, 429 o 5xx: se reintenta con
 * espera creciente) o ERROR_DEFINITIVO (número inválido, sin WhatsApp, plantilla rechazada: FALLIDO y respaldo).
 */
public record ResultadoEnvio(Tipo tipo, ProveedorMensajeria proveedor, String idProveedor, String error) {

	public enum Tipo {
		ACEPTADO, ERROR_REINTENTABLE, ERROR_DEFINITIVO
	}

	public ResultadoEnvio {
		Objects.requireNonNull(tipo, "tipo");
		if (tipo == Tipo.ACEPTADO && (proveedor == null || idProveedor == null || idProveedor.isBlank())) {
			throw new IllegalArgumentException("Un envío aceptado tiene proveedor e id");
		}
	}

	public static ResultadoEnvio aceptado(ProveedorMensajeria proveedor, String id) {
		return new ResultadoEnvio(Tipo.ACEPTADO, proveedor, id, null);
	}

	public static ResultadoEnvio reintentable(String error) {
		return new ResultadoEnvio(Tipo.ERROR_REINTENTABLE, null, null, error);
	}

	public static ResultadoEnvio definitivo(String error) {
		return new ResultadoEnvio(Tipo.ERROR_DEFINITIVO, null, null, error);
	}

	public boolean aceptado() {
		return tipo == Tipo.ACEPTADO;
	}
}
