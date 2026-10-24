package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository.PeriodoEvaluacionRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10, tanda 1 (sección 3.3): {@code sistema.academico} abre los periodos PROGRAMADOS cuyo inicio llegó (hoy en Lima;
 * en MySQL decide la hora de la base: {@code NEW.inicio <= cc_hoy_lima()} y la conexión de {@code cc_sistema}). Un periodo
 * no abre si el anterior sigue PROGRAMADO (los abre en orden en la misma pasada). El sistema no firma ni CIERRA: cerrar es
 * solo de Dirección. Lo llama solo {@code academico.curriculo.proceso.AperturaPeriodos}.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_ACADEMICO')")
public class AperturaDeSistema {

	private final PeriodoEvaluacionRepository periodos;

	private final AnioEscolarRepository anios;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public AperturaDeSistema(PeriodoEvaluacionRepository periodos, AnioEscolarRepository anios, AuditoriaService auditoria,
			Clock reloj) {
		this.periodos = periodos;
		this.anios = anios;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** Los PROGRAMADOS del colegio cuyo inicio ya llegó, en orden de año y número. */
	@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
	public List<Long> paraAbrir() {
		return periodos.findByEstadoAndInicioLessThanEqualOrderByAnioEscolarIdAscNumeroAsc(EstadoPeriodo.PROGRAMADO,
				LocalDate.now(reloj)).stream().map(PeriodoEvaluacion::getId).toList();
	}

	/** Abre uno (si todavía corresponde). @return si lo abrió */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean abrirEnSuFecha(Long periodoId) {
		Optional<PeriodoEvaluacion> encontrado = periodos.bloquear(periodoId)
				.filter(p -> p.getEstado() == EstadoPeriodo.PROGRAMADO);
		if (encontrado.isEmpty()) {
			return false;
		}
		PeriodoEvaluacion periodo = encontrado.get();
		LocalDate hoy = LocalDate.now(reloj);
		Optional<AnioEscolar> anio = anios.findById(periodo.getAnioEscolarId());
		if (periodo.getInicio().isAfter(hoy) || anio.isEmpty() || anio.get().cerrado()) {
			return false;
		}
		if (periodo.getNumero() > 1 && periodos.findByAnioEscolarIdAndNumero(periodo.getAnioEscolarId(),
				periodo.getNumero() - 1).map(a -> a.getEstado() == EstadoPeriodo.PROGRAMADO).orElse(true)) {
			return false;
		}
		periodo.abrir(ActorSistema.ACADEMICO.usuario(), LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
		periodos.saveAndFlush(periodo);
		auditoria.registrar(AccionAuditoria.PERIODO_ABIERTO, Periodos.ENTIDAD, periodo.getId().toString(),
				EstadoPeriodo.PROGRAMADO.name(), EstadoPeriodo.ABIERTO.name(), "sistema.academico abrió el "
						+ periodo.getNombre() + " de " + anio.get().getAnio() + " en su fecha de inicio. Desde ahora los "
						+ "docentes registran sus notas.");
		return true;
	}
}
