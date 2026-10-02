package pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto;

import java.time.LocalDateTime;

/** Una solicitud en la bandeja. {@code puedeResolver}: el usuario en sesión no la pidió ni preparó esa cuenta. */
public record SolicitudVista(Long id, String tipo, String tipoEtiqueta, String resumen, String motivo, String estado,
		String estadoEtiqueta, String estadoVariante, String solicitadoPor, LocalDateTime solicitadaEn,
		String resueltoPor, LocalDateTime resueltoEn, String comentario, boolean puedeResolver) {
}
