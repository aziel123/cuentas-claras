package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.SesionApoderado;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.matricula.dto.RenovacionFamilia;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.CanalRespuesta;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.EstadoRenovacion;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.RenovacionMatricula;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.RenovacionMatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * La renovación vista y respondida por la familia en el portal (sprint 5, pantalla 6). La familia sale SIEMPRE de la
 * cuenta en sesión, nunca de la URL: la renovación de otra familia es 404 (G17; en MySQL lo exige además el trigger).
 * Responder «sí» no crea deuda por sí mismo: después del commit, {@code sistema.matricula} reserva la matrícula con la
 * cuota del plan aprobado.
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
@Transactional
public class ServicioRenovacionFamilia {

	private final RenovacionMatriculaRepository renovaciones;

	private final PlanPensionRepository planes;

	private final SesionApoderado sesion;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	/** Sprint 7, tanda 2: la firma de la sesión de quien resuelve (sección 3.4). */
	private final FirmaSesion firmaSesion;

	public ServicioRenovacionFamilia(RenovacionMatriculaRepository renovaciones, PlanPensionRepository planes,
			SesionApoderado sesion, AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock reloj, FirmaSesion firmaSesion) {
		this.firmaSesion = firmaSesion;
		this.renovaciones = renovaciones;
		this.planes = planes;
		this.sesion = sesion;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public List<RenovacionFamilia> deMiFamilia() {
		return renovaciones.deFamilia(sesion.familiaId()).stream().map(this::vista).toList();
	}

	@Transactional(readOnly = true)
	public RenovacionFamilia una(Long id) {
		return vista(deMiFamilia(id));
	}

	/** «Sí, continúa» o «No continuará». Una sola vez y antes de la fecha límite. */
	public RenovacionFamilia responder(Long id, boolean continua) {
		Long familia = sesion.familiaId();
		RenovacionMatricula renovacion = renovaciones.bloquear(id).filter(r -> Objects.equals(r.getFamiliaId(), familia))
				.orElseThrow(() -> new RecursoNoEncontradoException("Renovación no encontrada"));
		firmaSesion.firmar(ClaveFirma.renovacion(renovacion.getId(), continua ? EstadoRenovacion.CONFIRMADA
				: EstadoRenovacion.NO_CONTINUA));
		renovacion.responder(continua, CanalRespuesta.PORTAL, SesionActual.usuario(), LocalDateTime.now(reloj));
		renovaciones.saveAndFlush(renovacion);
		auditoria.registrar(continua ? AccionAuditoria.RENOVACION_CONFIRMADA : AccionAuditoria.RENOVACION_NO_CONTINUA,
				"renovacion_matricula", id.toString(), "PROPUESTA", renovacion.getEstado().name(),
				"Alumno " + renovacion.getAlumno().nombreCompleto() + ", año " + renovacion.getAnioDestino().getAnio()
						+ " (respuesta en el portal).");
		eventos.publishEvent(new RenovacionRespondida(id, false, continua));
		if (continua) {
			eventos.publishEvent(new RenovacionConfirmada(ContextoColegio.actual(), id));
		}
		return vista(renovacion);
	}

	private RenovacionMatricula deMiFamilia(Long id) {
		Long familia = sesion.familiaId();
		return renovaciones.findById(id).filter(r -> Objects.equals(r.getFamiliaId(), familia))
				.orElseThrow(() -> new RecursoNoEncontradoException("Renovación no encontrada"));
	}

	private RenovacionFamilia vista(RenovacionMatricula r) {
		Optional<PlanPension> plan = planes.findByAnioEscolarIdAndNivelAndVigenteTrue(r.getAnioDestino().getId(),
				r.getGradoDestino().nivel()).filter(PlanPension::aprobado);
		return new RenovacionFamilia(r.getId(), nombreDePila(r.getAlumno().getNombres()), r.getAnioDestino().getAnio(),
				r.getGradoDestino().etiqueta(), r.getSeccionDestino().getNombre(),
				plan.map(PlanPension::getMontoMatricula).orElse(null),
				plan.map(PlanPension::getVencimientoMatricula).orElse(null), r.getVenceEn(), r.getEstado().etiqueta(),
				r.getEstado().variante(), r.getEstado() == EstadoRenovacion.PROPUESTA,
				r.getEstado() == EstadoRenovacion.CONFIRMADA || r.getEstado() == EstadoRenovacion.MATRICULADA);
	}

	/** Ley 29733: al alumno se le nombra solo por su nombre de pila. */
	public static String nombreDePila(String nombres) {
		String limpio = nombres == null ? "" : nombres.strip();
		int espacio = limpio.indexOf(' ');
		return espacio < 0 ? limpio : limpio.substring(0, espacio);
	}
}
