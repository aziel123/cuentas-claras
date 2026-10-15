package pe.edu.virgenmaria.cuentasclaras.academico.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.repository.AsignacionDocenteRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 9, tanda 1 (sección 3.2): {@code sistema.academico} termina las asignaciones VIGENTES que ya no deben dar acceso:
 * las de un año CERRADO y las de una cuenta desactivada, que ya no tiene el rol DOCENTE o que ahora combina Docente con
 * Caja o Administración. La aplicación ya no les muestra la sección (exige la cuenta activa y el rol en la sesión); esto
 * deja la base igual (las funciones de MySQL de las tandas 2 y 3 la leen). Lo llama solo el proceso
 * {@code academico.proceso.FinAsignaciones}, como el actor de sistema y con la conexión de {@code cc_sistema}.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_ACADEMICO')")
public class AsignacionesDeSistema {

	private final AsignacionDocenteRepository asignaciones;

	private final AnioEscolarRepository anios;

	private final UsuarioRepository usuarios;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public AsignacionesDeSistema(AsignacionDocenteRepository asignaciones, AnioEscolarRepository anios,
			UsuarioRepository usuarios, AuditoriaService auditoria, Clock reloj) {
		this.asignaciones = asignaciones;
		this.anios = anios;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Las asignaciones vigentes del colegio que ya no deben estarlo. */
	@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
	public List<Long> paraTerminar() {
		return asignaciones.findByEstadoOrderByIdAsc(EstadoAsignacion.VIGENTE).stream()
				.filter(a -> motivo(a).isPresent()).map(AsignacionDocente::getId).toList();
	}

	/** Termina una (si todavía corresponde) con el motivo y el actor de sistema. @return si la terminó */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean terminarPorSistema(Long asignacionId) {
		Optional<AsignacionDocente> encontrada = asignaciones.bloquear(asignacionId).filter(AsignacionDocente::esVigente);
		if (encontrada.isEmpty()) {
			return false;
		}
		AsignacionDocente asignacion = encontrada.get();
		Optional<String> motivo = motivo(asignacion);
		if (motivo.isEmpty()) {
			return false;
		}
		asignacion.terminar(ActorSistema.ACADEMICO.usuario(), motivo.get(),
				LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		asignaciones.saveAndFlush(asignacion);
		auditoria.registrar(AccionAuditoria.ASIGNACION_DOCENTE_TERMINADA, Asignaciones.ENTIDAD,
				asignacion.getId().toString(), EstadoAsignacion.VIGENTE.name(), EstadoAsignacion.TERMINADA.name(),
				"sistema.academico terminó la asignación de la cuenta " + asignacion.getUsuarioId() + " en la sección "
						+ asignacion.getSeccionId() + ": " + motivo.get());
		return true;
	}

	private Optional<String> motivo(AsignacionDocente a) {
		if (anios.findById(a.getAnioEscolarId()).map(AnioEscolar::cerrado).orElse(true)) {
			return Optional.of("El año escolar se cerró.");
		}
		Optional<Usuario> cuenta = usuarios.findById(a.getUsuarioId());
		if (cuenta.isEmpty() || !cuenta.get().isActivo()) {
			return Optional.of("La cuenta del docente se desactivó.");
		}
		if (!cuenta.get().getRoles().contains(Rol.DOCENTE)) {
			return Optional.of("La cuenta ya no tiene el rol Docente.");
		}
		if (Asignaciones.combinaConCobranza(cuenta.get())) {
			return Optional.of("La cuenta combina Docente con Caja o Administración (ve quién debe).");
		}
		return Optional.empty();
	}
}
