package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

/**
 * Resultado de crear un usuario o restablecer su clave. La clave temporal se muestra UNA sola vez y
 * no se guarda en ningún lado (en la base solo queda su hash).
 */
public record UsuarioCreado(Long id, String nombreUsuario, String nombreCompleto, String claveTemporal) {

	@Override
	public String toString() {
		// Nunca imprimir la clave temporal.
		return "UsuarioCreado[id=" + id + ", nombreUsuario=" + nombreUsuario + "]";
	}
}
