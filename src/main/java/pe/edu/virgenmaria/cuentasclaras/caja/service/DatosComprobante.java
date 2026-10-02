package pe.edu.virgenmaria.cuentasclaras.caja.service;

import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;

/**
 * A nombre de quién sale el comprobante: una boleta a un apoderado de la familia (por defecto, el responsable de pago)
 * o una factura con RUC y razón social.
 */
public record DatosComprobante(TipoComprobante tipo, Long apoderadoId, String ruc, String razonSocial) {
}
