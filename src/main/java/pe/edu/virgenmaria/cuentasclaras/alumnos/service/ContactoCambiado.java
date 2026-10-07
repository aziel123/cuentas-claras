package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

/**
 * Sprint 5: cambió el celular o el correo de un apoderado con su solicitud aprobada. La mensajería avisa al contacto
 * ANTERIOR en la misma transacción («Tu número dejó de recibir los avisos del colegio. Si no lo pediste, avísanos»).
 */
public record ContactoCambiado(Long apoderadoId, Long solicitudId, String telefonoAnterior, String correoAnterior) {
}
