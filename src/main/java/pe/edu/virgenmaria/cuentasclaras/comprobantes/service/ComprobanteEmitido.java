package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

/**
 * Se emitió un comprobante: se envía al OSE después del commit ({@link EnvioComprobantes}). Lleva el colegio porque el
 * envío corre en otro hilo, sin la sesión del usuario.
 */
public record ComprobanteEmitido(Long id, Long colegioId) {
}
