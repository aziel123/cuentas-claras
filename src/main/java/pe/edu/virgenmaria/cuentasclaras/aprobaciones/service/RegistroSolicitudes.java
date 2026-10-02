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

	public RegistroSolicitudes(SolicitudCambioRepository solicitudes, AuditoriaService auditoria) {
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
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
		auditoria.registrar(AccionAuditoria.SOLICITUD_CREADA, "solicitud_cambio", solicitud.getId().toString(), null,
				tipo.etiqueta() + ": " + solicitud.getResumen(), "Motivo: " + solicitud.getMotivo()
						+ ". Pendiente de aprobación de otra persona.");
		return solicitud;
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
