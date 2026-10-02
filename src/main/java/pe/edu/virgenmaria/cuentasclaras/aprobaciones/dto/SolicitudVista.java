package pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Una solicitud en la bandeja. {@code puedeResolver}: el usuario en sesión no la pidió, no es una persona involucrada
 * (la cajera del pago) ni preparó esas cuentas. {@code detalle}, {@code advertencia}, {@code pideComentario} (aprobar
 * exige un comentario) y {@code accionRechazo} («Rechazar» u «Observar») los da el manejador del tipo.
 */
public record SolicitudVista(Long id, String tipo, String tipoEtiqueta, String resumen, String motivo, String estado,
		String estadoEtiqueta, String estadoVariante, String solicitadoPor, LocalDateTime solicitadaEn,
		String resueltoPor, LocalDateTime resueltoEn, String comentario, boolean puedeResolver, List<String> detalle,
		String advertencia, boolean pideComentario, String accionRechazo) {
}
