package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

import java.time.LocalDateTime;

/**
 * Resultado de crear un usuario del personal o restablecer su acceso (sprint 5): SIN clave. El enlace de activación se
 * envió directo al celular o al correo del titular; aquí solo queda a dónde (enmascarado) y hasta cuándo sirve.
 */
public record UsuarioCreado(Long id, String nombreUsuario, String nombreCompleto, String canal, String destinoEnmascarado,
		LocalDateTime venceEn) {

	/** «Enlace enviado a WhatsApp +51 *** *** 321». */
	public String enviadoA() {
		return "Enlace enviado a " + canal + " " + destinoEnmascarado;
	}
}
