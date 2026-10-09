package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;

import java.time.LocalDateTime;

/**
 * Los campos de un evento de la bitácora que entran en su sello HMAC, en el orden de la forma canónica de
 * {@link SelladorAuditoria}. Sprint 7: permite sellar un evento leído con JDBC (verificación de un respaldo restaurado)
 * sin construir la entidad. {@code accion} va como texto (el nombre del enum).
 */
public record CamposSellados(long secuencia, Long colegioId, LocalDateTime ocurridoEn, Long usuarioId,
		String nombreUsuario, String roles, String accion, String entidad, String entidadId, String valorAnterior,
		String valorNuevo, String detalle, String ip) {

	static CamposSellados de(EventoAuditoria e) {
		return new CamposSellados(e.getSecuencia(), e.getColegioId(), e.getOcurridoEn(), e.getUsuarioId(),
				e.getNombreUsuario(), e.getRoles(), e.getAccion() == null ? null : e.getAccion().name(), e.getEntidad(),
				e.getEntidadId(), e.getValorAnterior(), e.getValorNuevo(), e.getDetalle(), e.getIp());
	}
}
