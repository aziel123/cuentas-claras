package pe.edu.virgenmaria.cuentasclaras.matricula.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ReservasMatricula;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.PlanPensionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.EstadoRenovacion;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.RenovacionMatricula;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.MatriculasReservadasRepository;
import pe.edu.virgenmaria.cuentasclaras.matricula.repository.RenovacionMatriculaRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lo que hace {@code sistema.matricula} (sprint 5, tanda 2), siempre dentro de la transacción del proceso que lo llama
 * ({@code matricula.proceso}): reservar la matrícula de una renovación confirmada, activar las reservadas con la matrícula
 * pagada, retirar las desistidas y vencer las propuestas sin respuesta. Ninguna persona lo llama (G18).
 */
@Service
@PreAuthorize("hasRole('SISTEMA_MATRICULA')")
@Transactional(propagation = Propagation.MANDATORY)
public class ProcesosMatricula {

	private static final Logger LOG = LoggerFactory.getLogger(ProcesosMatricula.class);

	private final RenovacionMatriculaRepository renovaciones;

	private final MatriculasReservadasRepository reservadas;

	private final MatriculaRepository matriculas;

	private final PlanPensionRepository planes;

	private final ReservasMatricula reservas;

	private final AuditoriaService auditoria;

	public ProcesosMatricula(RenovacionMatriculaRepository renovaciones, MatriculasReservadasRepository reservadas,
			MatriculaRepository matriculas, PlanPensionRepository planes, ReservasMatricula reservas,
			AuditoriaService auditoria) {
		this.renovaciones = renovaciones;
		this.reservadas = reservadas;
		this.matriculas = matriculas;
		this.planes = planes;
		this.reservas = reservas;
		this.auditoria = auditoria;
	}

	public List<Long> confirmadasSinMatricula() {
		return renovaciones.findByEstadoOrderByIdAsc(EstadoRenovacion.CONFIRMADA).stream()
				.map(RenovacionMatricula::getId).toList();
	}

	/**
	 * Reserva la matrícula de una renovación CONFIRMADA (con su cuota de matrícula del plan aprobado) y la pasa a
	 * MATRICULADA. Si el alumno ya tiene matrícula en ese año y sección (la registró Administración), solo la enlaza.
	 *
	 * @return {@code true} si quedó MATRICULADA
	 */
	public boolean reservar(Long renovacionId) {
		RenovacionMatricula renovacion = renovaciones.bloquear(renovacionId).orElse(null);
		if (renovacion == null || renovacion.getEstado() != EstadoRenovacion.CONFIRMADA) {
			return false;
		}
		Optional<Matricula> existente = matriculas.findByAlumnoIdAndAnioEscolarId(renovacion.getAlumno().getId(),
				renovacion.getAnioDestino().getId());
		Long matriculaId;
		if (existente.isPresent()) {
			if (!existente.get().getSeccion().getId().equals(renovacion.getSeccionDestino().getId())) {
				LOG.warn("La renovación {} no se reservó: el alumno ya tiene matrícula en otra sección de ese año.",
						renovacionId);
				return false;
			}
			matriculaId = existente.get().getId();
		}
		else {
			matriculaId = reservas.reservar(renovacion.getAlumno().getId(), renovacion.getSeccionDestino().getId());
		}
		// La matrícula ya está en la base (saveAndFlush): el trigger de la renovación la encuentra.
		renovacion.matricular(matriculaId);
		renovaciones.saveAndFlush(renovacion);
		return true;
	}

	/** Reservadas listas para activarse: su cuota de matrícula está pagada o exonerada, o su plan cobra 0. */
	public List<Long> paraActivar() {
		List<Long> ids = new ArrayList<>(reservadas.reservadasConMatriculaPagada());
		Set<Long> anuladas = new HashSet<>(reservadas.reservadasConMatriculaAnulada());
		for (Matricula m : reservadas.reservadasSinCuotaDeMatriculaVigente()) {
			if (anuladas.contains(m.getId())) {
				continue;
			}
			boolean sinMatricula = planes.findByAnioEscolarIdAndNivelAndVigenteTrue(m.getAnioEscolar().getId(), m.nivel())
					.filter(PlanPension::aprobado).filter(p -> p.getMontoMatricula().signum() == 0).isPresent();
			if (sinMatricula) {
				ids.add(m.getId());
			}
		}
		return ids;
	}

	public void activar(Long matriculaId) {
		reservas.activar(matriculaId);
	}

	public List<Long> desistidas() {
		return reservadas.reservadasConMatriculaAnulada();
	}

	public void desistir(Long matriculaId) {
		reservas.desistir(matriculaId, "Desistimiento: la cuota de matrícula se anuló con aprobación de otra persona.");
	}

	/** Las propuestas sin respuesta pasada su fecha límite vencen: no hay deuda. */
	public int vencer(LocalDate hoy) {
		List<RenovacionMatricula> vencidas = renovaciones.findByEstadoAndVenceEnBeforeOrderByIdAsc(
				EstadoRenovacion.PROPUESTA, hoy);
		for (RenovacionMatricula r : vencidas) {
			r.vencer();
			renovaciones.save(r);
		}
		if (!vencidas.isEmpty()) {
			auditoria.registrar(AccionAuditoria.RENOVACION_VENCIDA, "renovacion_matricula",
					vencidas.get(0).getId().toString(), "PROPUESTA", "VENCIDA", vencidas.size()
							+ " propuesta(s) de renovación vencieron sin respuesta al " + Calendario.formatear(hoy)
							+ ": no se generó deuda.");
		}
		return vencidas.size();
	}
}
