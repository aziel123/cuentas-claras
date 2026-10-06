package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

/**
 * La reconsulta nocturna encontró un comprobante que el sistema tiene como aceptado y el OSE no reconoce o tiene en
 * otro estado. Es una alerta crítica: el trigger no deja cambiar su estado, así que solo se registra y se avisa.
 */
public record ComprobanteNoCoincideOse(Long comprobanteId, String numero, String estadoSistema, String estadoOse) {
}
