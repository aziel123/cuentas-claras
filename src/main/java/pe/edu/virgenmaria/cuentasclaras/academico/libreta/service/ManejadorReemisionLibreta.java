package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.List;

/**
 * Sprint 10, tanda 3 (A3, decisión 188): la {@code REEMISION_LIBRETA} la pide Dirección y la aprueba SOLO Promotoría (otra
 * persona). Aprobar no escribe la libreta (la aplicación no tiene GRANT sobre {@code libreta_emitida}): la emite
 * {@code sistema.libretas} en su pasada siguiente, con motivo REEMISION, y avisa a la familia con LIBRETA_CORREGIDA. En MySQL
 * lo exige también trg_libreta_emitida_nace (la solicitud firmada, aprobada por PROMOTOR y pedida por DIRECTOR).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorReemisionLibreta implements ManejadorSolicitud {

	private final LibretaEmitidaRepository libretas;

	private final UsuarioRepository usuarios;

	private final AuditoriaService auditoria;

	public ManejadorReemisionLibreta(LibretaEmitidaRepository libretas, UsuarioRepository usuarios,
			AuditoriaService auditoria) {
		this.libretas = libretas;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.REEMISION_LIBRETA;
	}

	@Override
	public String entidad() {
		return RegeneracionLibreta.ENTIDAD;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		LibretaEmitida libreta = libretas.findById(solicitud.getEntidadId())
				.orElseThrow(() -> new ReglaNegocioException("La libreta de la solicitud ya no existe."));
		if (!tiene(aprobador, Rol.PROMOTOR)) {
			throw new ReglaNegocioException("La reemisión de una libreta la aprueba Promotoría.");
		}
		if (!tiene(solicitud.getSolicitadoPor(), Rol.DIRECTOR)) {
			throw new ReglaNegocioException("La reemisión la pide Dirección: quien la pidió ya no es de Dirección.");
		}
		boolean vigente = libretas.findFirstByPeriodoIdAndMatriculaIdOrderByNumeroVersionDesc(libreta.getPeriodoId(),
				libreta.getMatriculaId()).map(l -> l.getId().equals(libreta.getId())).orElse(false);
		if (!vigente) {
			throw new ReglaNegocioException("Ya hay una versión más nueva de esta libreta: rechaza la solicitud.");
		}
		auditoria.registrar(AccionAuditoria.SOLICITUD_APROBADA, RegeneracionLibreta.ENTIDAD, libreta.getId().toString(),
				null, "REEMISION", "Aprobó reemitir la libreta " + libreta.codigoVerificacion() + " (alumno "
						+ libreta.getAlumnoId() + "): sistema.libretas emitirá la versión " + (libreta.getNumeroVersion() + 1)
						+ ". Aprobó " + aprobador + " (solicitud " + solicitud.getId() + ").");
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		return libretas.findById(solicitud.getEntidadId())
				.map(l -> List.of("Libreta: " + l.codigoVerificacion() + " (versión " + l.getNumeroVersion() + ")",
						"Alumno: " + l.cabecera().alumnoNombre(),
						"Qué pasó: al regenerarla, no coincide con su huella (alguien cambió la base después de emitirla)",
						"Al aprobar: el sistema emite la versión siguiente con lo que hay hoy en la base"))
				.orElse(List.of("La libreta ya no existe."));
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "La versión nueva tomará lo que hay hoy en la base. Antes de aprobar, revisa en la bitácora qué cambió y por "
				+ "qué: si alguien alteró notas, corrígelas por su solicitud.";
	}

	private boolean tiene(String nombreUsuario, Rol rol) {
		return usuarios.findByNombreUsuario(nombreUsuario).filter(Usuario::isActivo).map(u -> u.getRoles().contains(rol))
				.orElse(false);
	}
}
