package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

/**
 * Sprint 6, tanda 2: el contacto actual de una persona del personal, enmascarado (nunca se muestra entero).
 *
 * @param pendiente si ya hay una solicitud de cambio esperando aprobación
 */
public record ContactoPersonalVista(Long usuarioId, String nombreCompleto, String celular, String correo,
		boolean pendiente) {
}
