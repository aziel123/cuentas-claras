package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

/**
 * Sprint 6, tanda 2 (P6): cambió el celular o el correo de una persona del personal con su solicitud aprobada por otra
 * persona. La mensajería avisa al contacto ANTERIOR en la misma transacción («este número dejó de recibir los mensajes de
 * tu cuenta; si no lo pediste, avisa a Promotoría»): si alguien desvió el resumen, la huella o las alertas, la titular se
 * entera en su número de siempre.
 */
public record ContactoPersonalCambiado(Long usuarioId, Long solicitudId, String telefonoAnterior, String correoAnterior) {
}
