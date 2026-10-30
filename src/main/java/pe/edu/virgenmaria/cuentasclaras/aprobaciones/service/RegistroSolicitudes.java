package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.List;
import java.util.Map;

/**
 * Crea solicitudes de cambio. Sin {@code @PreAuthorize} propio: solo lo usan servicios que ya exigieron el rol de quien
 * pide (regla ArchUnit), dentro de su transacción. Queda resaltado en la bitácora.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class RegistroSolicitudes {

	private final SolicitudCambioRepository solicitudes;

	private final AuditoriaService auditoria;

	private final pe.edu.virgenmaria.cuentasclaras.seguridad.service.SegundaFirma segundaFirma;

	/** Sprint 11, tanda 1c: la bandeja aplica la directa (perezosa: sus manejadores usan este registro). */
	private final org.springframework.beans.factory.ObjectProvider<BandejaAprobaciones> bandeja;

	public RegistroSolicitudes(SolicitudCambioRepository solicitudes, AuditoriaService auditoria,
			pe.edu.virgenmaria.cuentasclaras.seguridad.service.SegundaFirma segundaFirma,
			org.springframework.beans.factory.ObjectProvider<BandejaAprobaciones> bandeja) {
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
		this.segundaFirma = segundaFirma;
		this.bandeja = bandeja;
	}

	/**
	 * @param resumen lo que se pide, en lenguaje claro y con datos enmascarados («Retirar a Mateo desde el 01/10/2026»)
	 */
	public SolicitudCambio crear(TipoSolicitud tipo, String entidad, Long entidadId, String resumen,
			Map<String, String> datos, String motivo) {
		if (solicitudes.existsByTipoAndEntidadAndEntidadIdAndEstado(tipo, entidad, entidadId, EstadoSolicitud.PENDIENTE)) {
			throw new ReglaNegocioException("Ya hay una solicitud de «" + tipo.etiqueta().toLowerCase()
					+ "» pendiente para esto: espera a que Promotoría o Dirección la resuelva.");
		}
		SolicitudCambio solicitud = solicitudes.saveAndFlush(SolicitudCambio.nueva(tipo, entidad, entidadId, resumen,
				DatosSolicitud.escribir(datos), motivo, usuario()));
		boolean directa = segundaFirma.aplicaDirecto(solicitud.getSolicitadoPor());
		// Lo de identidad (contacto del personal) se resuelve en la ruta de cc_sistema: la Promotoría lo aplica desde la
		// bandeja («Aprobar y aplicar»), no al pedirlo.
		boolean enDosPasos = tipo.sensible() || BandejaAprobaciones.TIPOS_DE_IDENTIDAD.contains(tipo);
		auditoria.registrar(AccionAuditoria.SOLICITUD_CREADA, "solicitud_cambio", solicitud.getId().toString(), null,
				tipo.etiqueta() + ": " + solicitud.getResumen(), "Motivo: " + solicitud.getMotivo() + (!directa
						? ". Pendiente de aprobación de otra persona."
						: tipo.sensible() ? ". La pidió una Promotoría: la aplica ella con su código por WhatsApp."
								: enDosPasos ? ". La pidió una Promotoría: la aplica ella desde la bandeja."
										: ". La pidió una Promotoría: se aplica directo al terminar la operación."));
		// Sprint 11, tanda 1c (sección 3.16): lo que pide una Promotoría y no es sensible se aplica directo, al final de
		// ESTA transacción (cuando quien la pidió ya enlazó lo suyo: la asignación, el comunicado). Si algo falla, nada se
		// guarda. Lo sensible queda pendiente hasta que ella lo aplique con su código («Aprobar y aplicar»).
		if (directa && !enDosPasos
				&& org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
			Long id = solicitud.getId();
			org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
					new org.springframework.transaction.support.TransactionSynchronization() {
						@Override
						public void beforeCommit(boolean soloLectura) {
							bandeja.getObject().aplicarDirectaAlPedir(id);
						}
					});
		}
		return solicitud;
	}

	/**
	 * Sprint 11, tanda 1c (A1c-1): la solicitud pendiente de cambio de contacto de esta cuenta (para que su dueña la
	 * confirme desde su contacto anterior).
	 */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public java.util.Optional<SolicitudCambio> pendienteDeContacto(Long solicitudId, Long usuarioId) {
		return solicitudes.findById(solicitudId).filter(s -> s.estaPendiente()
				&& s.getTipo() == TipoSolicitud.CAMBIO_CONTACTO_PERSONAL && "usuario".equals(s.getEntidad())
				&& s.getEntidadId().equals(usuarioId));
	}

	/** Sprint 11, tanda 1c (A1c-1): guarda la confirmación desde el contacto anterior (con flush: la base la valida). */
	public void confirmarDesdeContactoAnterior(SolicitudCambio solicitud, Long mensajeId, String escritoSha256) {
		solicitud.confirmarDesdeContactoAnterior(mensajeId, escritoSha256);
		solicitudes.saveAndFlush(solicitud);
	}

	/** Resúmenes de las solicitudes pendientes de una entidad (para mostrarlas en su ficha). */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public List<String> pendientesDe(String entidad, Long entidadId) {
		return solicitudes.findByEntidadAndEntidadIdAndEstadoOrderByIdAsc(entidad, entidadId, EstadoSolicitud.PENDIENTE)
				.stream().map(s -> s.getTipo().etiqueta() + ": " + s.getResumen() + " (pedida por " + s.getSolicitadoPor()
						+ ")")
				.toList();
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}
}
