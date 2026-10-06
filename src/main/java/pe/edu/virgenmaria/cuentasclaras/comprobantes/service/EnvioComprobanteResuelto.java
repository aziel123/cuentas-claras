package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;

/**
 * El OSE resolvió un comprobante (ACEPTADO, OBSERVADO o RECHAZADO). Se publica dentro de la transacción del envío para
 * que la bitácora lo registre en ella (comprobantes no depende de auditoría: lo audita quien escucha).
 */
public record EnvioComprobanteResuelto(Long comprobanteId, String numero, String tipo, EstadoEnvio estado,
		String respuesta, String codigoRespuesta, int intentos) {
}
