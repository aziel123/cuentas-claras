package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

import java.time.LocalDateTime;

/**
 * Sprint 5: el acceso quedó enviado DIRECTO a su titular. Solo dice por qué canal y a qué contacto enmascarado
 * («WhatsApp», «+51 *** *** 321») y hasta cuándo sirve el enlace (aproximado: el enlace se genera al enviarlo). Nunca
 * lleva el enlace ni una clave.
 */
public record AccesoEnviado(Long usuarioId, String nombreUsuario, String nombreCompleto, String canal,
		String destinoEnmascarado, LocalDateTime venceEn) {

	/** «Enlace enviado a WhatsApp +51 *** *** 321». */
	public String enviadoA() {
		return "Enlace enviado a " + canal + " " + destinoEnmascarado;
	}
}
