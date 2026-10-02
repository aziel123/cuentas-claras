package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

/**
 * Fila de la lista de usuarios.
 *
 * @param roles  roles para mostrar ("Caja", "Dirección · Administración")
 * @param estado ACTIVO, BLOQUEADO, CLAVE_PENDIENTE o INACTIVO
 */
public record UsuarioResumen(Long id, String nombreUsuario, String nombreCompleto, String roles, String estado) {
}
