package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

import java.time.LocalDateTime;

/**
 * La cuenta en línea del apoderado recién creada o restablecida (S4-M2): SIN clave. {@code enlace} es la ruta del enlace
 * de un solo uso (se arma la dirección completa en la pantalla) y {@code venceEn}, hasta cuándo sirve.
 */
public record AccesoCreado(Long id, String nombreUsuario, String nombreCompleto, String enlace, LocalDateTime venceEn) {

	@Override
	public String toString() {
		return "AccesoCreado[id=" + id + ", usuario=" + nombreUsuario + "]"; // sin el enlace: puede terminar en un log
	}
}
