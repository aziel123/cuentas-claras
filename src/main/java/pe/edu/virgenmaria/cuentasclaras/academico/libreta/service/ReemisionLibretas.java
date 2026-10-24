package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.Map;

/**
 * Sprint 10, tanda 3 (A3, decisión 188): Dirección pide reemitir una libreta cuya huella NO coincide (la regeneración dejó
 * {@code LIBRETA_HUELLA_NO_COINCIDE}); Promotoría la aprueba en la bandeja ({@link ManejadorReemisionLibreta}) y
 * {@code sistema.libretas} emite la versión siguiente con motivo REEMISION. Una libreta que coincide no se reemite: no hay
 * forma de emitir una versión sin una corrección aprobada o una revisión de integridad.
 */
@Service
@PreAuthorize("hasRole('DIRECTOR')")
public class ReemisionLibretas {

	public static final int MIN_MOTIVO = 10;

	private final LibretaEmitidaRepository libretas;

	private final RegistroSolicitudes solicitudes;

	private final AuditoriaService auditoria;

	public ReemisionLibretas(LibretaEmitidaRepository libretas, RegistroSolicitudes solicitudes,
			AuditoriaService auditoria) {
		this.libretas = libretas;
		this.solicitudes = solicitudes;
		this.auditoria = auditoria;
	}

	/** @return la solicitud creada */
	@Transactional
	public Long pedir(long libretaId, String motivo) {
		LibretaEmitida libreta = libretas.findById(libretaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Libreta no encontrada"));
		if (motivo == null || motivo.strip().length() < MIN_MOTIVO) {
			throw new ReglaNegocioException("Escribe el motivo de la reemisión (al menos " + MIN_MOTIVO + " caracteres).");
		}
		if (!auditoria.existeSobre(AccionAuditoria.LIBRETA_HUELLA_NO_COINCIDE, RegeneracionLibreta.ENTIDAD,
				libreta.getId().toString())) {
			throw new ReglaNegocioException("Esta libreta coincide con su huella: no hace falta reemitirla.");
		}
		boolean vigente = libretas.findFirstByPeriodoIdAndMatriculaIdOrderByNumeroVersionDesc(libreta.getPeriodoId(),
				libreta.getMatriculaId()).map(l -> l.getId().equals(libreta.getId())).orElse(false);
		if (!vigente) {
			throw new ReglaNegocioException("Ya hay una versión más nueva de esta libreta.");
		}
		SolicitudCambio solicitud = solicitudes.crear(TipoSolicitud.REEMISION_LIBRETA, RegeneracionLibreta.ENTIDAD,
				libreta.getId(), "Reemitir la libreta " + libreta.codigoVerificacion() + " (versión "
						+ libreta.getNumeroVersion() + ") tras una revisión de integridad",
				Map.of("libreta_id", libreta.getId().toString(), "huella", libreta.getHuellaContenido()), motivo.strip());
		return solicitud.getId();
	}
}
