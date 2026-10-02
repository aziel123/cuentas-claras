package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.BandejaVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ControlParticipantes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Bandeja de aprobaciones (Promotoría y Dirección): aprueba o rechaza las solicitudes. Quien la pidió (o quien creó o
 * restableció la clave de esa cuenta en los últimos 30 días) no la resuelve: el intento queda auditado. Al aprobar,
 * el manejador del tipo aplica el cambio en la misma transacción.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class BandejaAprobaciones {

	private final SolicitudCambioRepository solicitudes;

	/** Los manejadores exigen una transacción abierta (también {@code tipo()}): se eligen dentro de ella. */
	private final List<ManejadorSolicitud> manejadores;

	private final ControlParticipantes participantes;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public BandejaAprobaciones(SolicitudCambioRepository solicitudes, List<ManejadorSolicitud> manejadores,
			ControlParticipantes participantes, AuditoriaService auditoria, Clock reloj) {
		this.solicitudes = solicitudes;
		this.manejadores = List.copyOf(manejadores);
		this.participantes = participantes;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	public BandejaVista bandeja() {
		String usuario = usuario();
		return new BandejaVista(
				solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream().map(s -> vista(s, usuario)).toList(),
				solicitudes.findTop30ByEstadoNotOrderByResueltoEnDescIdDesc(EstadoSolicitud.PENDIENTE).stream()
						.map(s -> vista(s, usuario)).toList(),
				solicitudes.findByTipoOrderByIdDesc(TipoSolicitud.FECHA_MATRICULA).stream().map(s -> vista(s, usuario))
						.toList());
	}

	@Transactional(noRollbackFor = AutoaprobacionSolicitudException.class)
	public void aprobar(Long id, String comentario) {
		SolicitudCambio solicitud = pendiente(id);
		String usuario = usuario();
		exigirOtraPersona(solicitud, usuario, "aprobar");
		manejadorDe(solicitud.getTipo()).aplicar(solicitud, usuario);
		solicitud.aprobar(usuario, comentario, ahora());
		solicitudes.saveAndFlush(solicitud);
		auditoria.registrar(AccionAuditoria.SOLICITUD_APROBADA, "solicitud_cambio", id.toString(),
				EstadoSolicitud.PENDIENTE.name(), EstadoSolicitud.APROBADA.name(), solicitud.getTipo().etiqueta() + ": "
						+ solicitud.getResumen() + ". Pedida por " + solicitud.getSolicitadoPor() + ".");
	}

	@Transactional(noRollbackFor = AutoaprobacionSolicitudException.class)
	public void rechazar(Long id, String motivo) {
		SolicitudCambio solicitud = pendiente(id);
		String usuario = usuario();
		exigirOtraPersona(solicitud, usuario, "rechazar");
		solicitud.rechazar(usuario, motivo, ahora());
		manejadorDe(solicitud.getTipo()).alRechazar(solicitud);
		solicitudes.saveAndFlush(solicitud);
		auditoria.registrar(AccionAuditoria.SOLICITUD_RECHAZADA, "solicitud_cambio", id.toString(),
				EstadoSolicitud.PENDIENTE.name(), EstadoSolicitud.RECHAZADA.name(), solicitud.getTipo().etiqueta() + ": "
						+ solicitud.getResumen() + ". Motivo del rechazo: " + solicitud.getComentario());
	}

	private ManejadorSolicitud manejadorDe(TipoSolicitud tipo) {
		List<ManejadorSolicitud> delTipo = manejadores.stream().filter(m -> m.tipo() == tipo).toList();
		if (delTipo.size() != 1) {
			throw new IllegalStateException(delTipo.size() + " manejadores para " + tipo);
		}
		return delTipo.getFirst();
	}

	private SolicitudCambio pendiente(Long id) {
		SolicitudCambio solicitud = solicitudes.bloquear(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
		if (!solicitud.estaPendiente()) {
			throw new ReglaNegocioException("La solicitud ya fue " + solicitud.getEstado().etiqueta().toLowerCase()
					+ " por " + solicitud.getResueltoPor() + ".");
		}
		return solicitud;
	}

	private void exigirOtraPersona(SolicitudCambio solicitud, String usuario, String accion) {
		Set<String> involucrados = participantes.ampliar(Set.of(solicitud.getSolicitadoPor()));
		if (involucrados.contains(usuario)) {
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "solicitud_cambio",
					solicitud.getId().toString(), null, solicitud.getTipo().etiqueta() + ": " + solicitud.getResumen(),
					"Intentó " + accion + " una solicitud que pidió (o pidió una cuenta que preparó). Se rechazó.");
			throw new AutoaprobacionSolicitudException("No puedes " + accion + " una solicitud que tú pediste (o que "
					+ "pidió una cuenta que creaste o a la que le restableciste la clave): debe hacerlo otra persona.");
		}
	}

	private SolicitudVista vista(SolicitudCambio s, String usuario) {
		boolean puede = s.estaPendiente() && !participantes.ampliar(Set.of(s.getSolicitadoPor())).contains(usuario);
		return new SolicitudVista(s.getId(), s.getTipo().name(), s.getTipo().etiqueta(), s.getResumen(), s.getMotivo(),
				s.getEstado().name(), s.getEstado().etiqueta(), s.getEstado().variante(), s.getSolicitadoPor(),
				s.getCreadoEn(), s.getResueltoPor(), s.getResueltoEn(), s.getComentario(), puede);
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}
}
