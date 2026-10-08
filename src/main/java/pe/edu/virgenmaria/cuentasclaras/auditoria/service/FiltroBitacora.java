package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;

import java.time.LocalDate;

/**
 * Filtros de la bitácora. {@code null} o vacío = sin filtro.
 *
 * @param soloRevisar solo intentos fallidos, rechazos, restablecimientos y altas o cambios de roles sensibles
 */
public record FiltroBitacora(LocalDate desde, LocalDate hasta, String nombreUsuario, AccionAuditoria accion,
		boolean soloRevisar) {

	public FiltroBitacora {
		nombreUsuario = nombreUsuario == null || nombreUsuario.isBlank() ? null : nombreUsuario.strip().toLowerCase();
	}

	public static FiltroBitacora fechas(LocalDate desde, LocalDate hasta) {
		return new FiltroBitacora(desde, hasta, null, null, false);
	}
}
