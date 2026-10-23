package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.ManejadorSolicitud;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Sprint 9, tanda 1 (sección 3.2, E4): aprueba o rechaza la solicitud {@code ASIGNACION_DOCENTE} desde la bandeja (que ya
 * comprobó que quien resuelve es otra persona de Promotoría o Dirección, firmó y dejó la solicitud APROBADA o RECHAZADA).
 * El docente asignado es un INVOLUCRADO: aunque tenga el rol de Dirección, no aprueba su propia asignación. Al aprobar
 * vuelve a validar (la cuenta sigue activa con el rol DOCENTE, la sección sigue sin otro tutor vigente) y la asignación
 * pasa a VIGENTE: desde ese momento el docente ve los nombres de los alumnos de la sección. En MySQL lo exige
 * trg_asignacion_docente_estado (la solicitud aprobada y firmada, por otra persona que no es el docente).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ManejadorAsignacionDocente implements ManejadorSolicitud {

	private final AsignacionDocenteRepository asignaciones;

	private final SolicitudCambioRepository solicitudes;

	private final UsuarioRepository usuarios;

	private final SeccionRepository secciones;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ManejadorAsignacionDocente(AsignacionDocenteRepository asignaciones, SolicitudCambioRepository solicitudes,
			UsuarioRepository usuarios, SeccionRepository secciones, AuditoriaService auditoria, Clock reloj) {
		this.asignaciones = asignaciones;
		this.solicitudes = solicitudes;
		this.usuarios = usuarios;
		this.secciones = secciones;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	public TipoSolicitud tipo() {
		return TipoSolicitud.ASIGNACION_DOCENTE;
	}

	@Override
	public String entidad() {
		return Asignaciones.ENTIDAD;
	}

	@Override
	public void aplicar(SolicitudCambio solicitud, String aprobador) {
		AsignacionDocente asignacion = propuestaDe(solicitud);
		Usuario docente = usuarios.findById(asignacion.getUsuarioId())
				.orElseThrow(() -> new ReglaNegocioException("La cuenta del docente ya no existe."));
		if (docente.getNombreUsuario().equals(aprobador)) {
			throw new ReglaNegocioException("No puedes aprobar tu propia asignación: debe hacerlo otra persona.");
		}
		if (!docente.isActivo() || docente.getApoderadoId() != null || !docente.getRoles().contains(Rol.DOCENTE)) {
			throw new ReglaNegocioException("La cuenta de " + docente.getNombreCompleto() + " ya no es un docente activo: "
					+ "rechaza la solicitud.");
		}
		if (Asignaciones.combinaConCobranza(docente)) {
			throw new ReglaNegocioException("La cuenta de " + docente.getNombreCompleto() + " combina Docente con Caja o "
					+ "Administración (ve quién debe): corrígela en Usuarios o rechaza la solicitud.");
		}
		if (asignacion.getTipo() == TipoAsignacion.TUTOR && asignaciones.findFirstBySeccionIdAndTipoAndEstado(
				asignacion.getSeccionId(), TipoAsignacion.TUTOR, EstadoAsignacion.VIGENTE).isPresent()) {
			throw new ReglaNegocioException("La sección ya tiene tutor(a) vigente: termina esa asignación o rechaza esta.");
		}
		asignacion.aprobar(aprobador, ahora());
		asignaciones.saveAndFlush(asignacion);
		auditoria.registrar(AccionAuditoria.ASIGNACION_DOCENTE_APROBADA, Asignaciones.ENTIDAD,
				asignacion.getId().toString(), EstadoAsignacion.PROPUESTA.name(), EstadoAsignacion.VIGENTE.name(),
				"Aprobó la asignación de " + solicitud.getResumen() + ". Propuso " + asignacion.getPropuestoPor()
						+ " y aprobó " + aprobador + " (solicitud " + solicitud.getId() + "). Desde ahora el docente ve los "
						+ "nombres de los alumnos de la sección.");
	}

	@Override
	public void alRechazar(SolicitudCambio solicitud) {
		AsignacionDocente asignacion = propuestaDe(solicitud);
		// La solicitud, RECHAZADA, va primero: trg_asignacion_docente_estado la exige así al rechazar la asignación.
		solicitudes.saveAndFlush(solicitud);
		asignacion.rechazar(usuario(), ahora());
		asignaciones.saveAndFlush(asignacion);
		auditoria.registrar(AccionAuditoria.ASIGNACION_DOCENTE_RECHAZADA, Asignaciones.ENTIDAD,
				asignacion.getId().toString(), EstadoAsignacion.PROPUESTA.name(), EstadoAsignacion.RECHAZADA.name(),
				"Rechazó la asignación de " + solicitud.getResumen() + " que propuso " + asignacion.getPropuestoPor()
						+ " (solicitud " + solicitud.getId() + ").");
	}

	/** El docente asignado tampoco la resuelve (aunque tenga el rol de Dirección): E4. */
	@Override
	public Set<String> involucrados(SolicitudCambio solicitud) {
		return asignaciones.findById(Objects.requireNonNull(solicitud.getEntidadId(), "entidadId"))
				.flatMap(a -> usuarios.findById(a.getUsuarioId())).map(u -> Set.of(u.getNombreUsuario())).orElse(Set.of());
	}

	@Override
	public List<String> detalle(SolicitudCambio solicitud) {
		Optional<AsignacionDocente> encontrada = asignaciones.findById(solicitud.getEntidadId());
		if (encontrada.isEmpty()) {
			return List.of();
		}
		AsignacionDocente a = encontrada.get();
		List<String> lineas = new ArrayList<>();
		usuarios.findById(a.getUsuarioId()).ifPresent(u -> lineas.add("Docente: " + u.getNombreCompleto() + " ("
				+ u.getNombreUsuario() + ")" + (u.getRoles().contains(Rol.DIRECTOR) ? ". También tiene el rol de Dirección"
						: "")));
		secciones.findById(a.getSeccionId()).ifPresent(s -> lineas.add("Sección: " + s.etiqueta() + " "
				+ s.getAnioEscolar().getAnio()));
		lineas.add("Qué hará: " + a.getTipo().etiqueta() + (a.getAreas() == null ? "" : " (" + a.getAreas() + ")"));
		if (a.getTipo() == TipoAsignacion.TUTOR) {
			asignaciones.findFirstBySeccionIdAndTipoAndEstado(a.getSeccionId(), TipoAsignacion.TUTOR,
					EstadoAsignacion.VIGENTE).flatMap(t -> usuarios.findById(t.getUsuarioId())).ifPresent(t -> lineas
							.add("La sección ya tiene tutor(a) vigente: " + t.getNombreCompleto()
									+ ". Primero hay que terminar esa asignación."));
		}
		return lineas;
	}

	@Override
	public String advertencia(SolicitudCambio solicitud) {
		return "Al aprobar, esta persona verá los nombres de los alumnos de la sección (nunca sus documentos, contactos, "
				+ "deudas ni pagos). Confirma con la Dirección que de verdad enseña en ella.";
	}

	private AsignacionDocente propuestaDe(SolicitudCambio solicitud) {
		AsignacionDocente asignacion = asignaciones.bloquear(Objects.requireNonNull(solicitud.getEntidadId(), "entidadId"))
				.orElseThrow(() -> new ReglaNegocioException("La asignación de la solicitud ya no existe."));
		if (asignacion.getEstado() != EstadoAsignacion.PROPUESTA || !solicitud.getId().equals(asignacion.getSolicitudId())) {
			throw new ReglaNegocioException("Esta asignación ya no está por aprobar ("
					+ asignacion.getEstado().etiqueta().toLowerCase(java.util.Locale.ROOT) + ").");
		}
		return asignacion;
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}
}
