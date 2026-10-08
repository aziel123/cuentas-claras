package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

/**
 * Datos del usuario en sesión para la barra superior. Nunca incluye la clave ni su hash.
 */
public record SesionVista(String nombreCompleto, String nombreUsuario, String rol, String colegio,
		boolean clavePendiente) {
}
