package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Ficha de un usuario. Nunca incluye la clave ni su hash.
 *
 * @param esUnoMismo     es el usuario en sesión: no puede modificarse a sí mismo
 * @param puedeGestionar quien mira puede cambiarlo (jerarquía: Dirección no toca a Promotoría ni a Dirección)
 */
public record UsuarioDetalle(Long id, String nombreUsuario, String nombreCompleto, String correo, Set<Rol> roles,
		String rolesTexto, String estado, boolean activo, boolean bloqueado, LocalDateTime bloqueadoHasta,
		boolean debeCambiarClave, LocalDateTime ultimoIngresoEn, LocalDateTime creadoEn, String creadoPor,
		LocalDateTime desactivadoEn, String desactivadoPor, boolean esUnoMismo, boolean puedeGestionar) {
}
