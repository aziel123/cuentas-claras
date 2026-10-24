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

	private final FotoLibreta foto;

	public ManejadorReemisionLibreta(LibretaEmitidaRepository libretas, UsuarioRepository usuarios,
			AuditoriaService auditoria, FotoLibreta foto) {
		this.libretas = libretas;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.foto = foto;
	}

	/**
	 * Correcciones del sprint 10 (hallazgo 2): en qué difieren los niveles emitidos de los que da la base hoy. Vacía si
	 * coinciden (o si la libreta es anterior a V38 y no guardó sus niveles).
	 */
	List<String> diferencias(LibretaEmitida libreta) {
		if (libreta.getNiveles() == null) {
			return List.of();
		}
		return FotoLibreta.diferencias(libreta.getNiveles(), FotoLibreta.niveles(foto.armar(libreta)));
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
		// Hallazgo 2: la reemisión vuelve a emitir lo emitido. Si la base dice hoy otros niveles, alguien los cambió: no se
		// reemite; se corrige cada nota por su solicitud (y sale la versión corregida).
		List<String> distintas = diferencias(libreta);
		if (!distintas.isEmpty()) {
			throw new ReglaNegocioException("No se puede reemitir: la base tiene hoy " + distintas.size() + " nivel(es) "
					+ "distinto(s) de los emitidos (" + String.join("; ", distintas.subList(0, Math.min(3,
							distintas.size()))) + "). Rechaza la solicitud y corrige esas notas por su solicitud.");
		}
		auditoria.registrar(AccionAuditoria.SOLICITUD_APROBADA, RegeneracionLibreta.ENTIDAD, libreta.getId().toString(),
				null, "REEMISION", "Aprobó reemitir la libreta " + libreta.codigoVerificacion() + " (alumno "
						+ libreta.getAlumnoId() + "): sistema.libretas emitirá la versión " + (libreta.getNumeroVersion() + 1)
						+ ". Aprobó " + aprobador + " (solicitud " + solicitud.getId() + ").");
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		return libretas.findById(solicitud.getEntidadId()).map(l -> {
			List<String> lineas = new java.util.ArrayList<>(List.of("Libreta: " + l.codigoVerificacion() + " (versión "
					+ l.getNumeroVersion() + ")", "Alumno: " + l.cabecera().alumnoNombre(),
					"Qué pasó: al regenerarla, no coincide con su huella (alguien cambió la base después de emitirla)"));
			if (l.getNiveles() == null) {
				lineas.add("Al aprobar: el sistema emite la versión siguiente (libreta anterior a V38: sin sus niveles "
						+ "guardados, revisa la bitácora)");
				return lineas;
			}
			List<String> distintas = diferencias(l);
			if (distintas.isEmpty()) {
				lineas.add("Niveles: los de hoy son los emitidos (cambió otro dato: conclusión, asistencia o cabecera)");
				lineas.add("Al aprobar: el sistema vuelve a emitir los mismos niveles");
			}
			else {
				lineas.add("Niveles distintos de los emitidos (" + distintas.size() + "): " + String.join("; ", distintas));
				lineas.add("No se puede aprobar: rechaza la solicitud y corrige esas notas por su solicitud");
			}
			return lineas;
		}).orElse(List.of("La libreta ya no existe."));
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "La versión nueva vuelve a emitir los niveles emitidos. Si la base tiene hoy otros niveles (se listan arriba), "
				+ "no se reemite: alguien alteró notas y se corrigen por su solicitud.";
	}

	private boolean tiene(String nombreUsuario, Rol rol) {
		return usuarios.findByNombreUsuario(nombreUsuario).filter(Usuario::isActivo).map(u -> u.getRoles().contains(rol))
				.orElse(false);
	}
}
