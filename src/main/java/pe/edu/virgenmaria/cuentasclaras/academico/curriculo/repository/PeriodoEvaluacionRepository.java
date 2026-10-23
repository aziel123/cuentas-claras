package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10, tanda 1: los periodos de evaluación del colegio actual (los filtra {@code @TenantId}). Sin {@code delete*} ni
 * {@code @Modifying}: cambian solo por sus métodos (y en MySQL, por columna y con trg_periodo_evaluacion_cambio).
 */
public interface PeriodoEvaluacionRepository extends Repository<PeriodoEvaluacion, Long> {

	PeriodoEvaluacion saveAndFlush(PeriodoEvaluacion periodo);

	Optional<PeriodoEvaluacion> findById(Long id);

	/** Lee y bloquea el periodo (abrir, cerrar y mover fechas a la vez se serializan). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PeriodoEvaluacion p where p.id = :id")
	Optional<PeriodoEvaluacion> bloquear(@Param("id") Long id);

	List<PeriodoEvaluacion> findByAnioEscolarIdOrderByNumeroAsc(Long anioEscolarId);

	Optional<PeriodoEvaluacion> findByAnioEscolarIdAndNumero(Long anioEscolarId, int numero);

	boolean existsByAnioEscolarIdAndEstadoNot(Long anioEscolarId, EstadoPeriodo estado);

	/** Los PROGRAMADOS cuyo inicio ya llegó (AperturaPeriodos), en orden de año y número. */
	List<PeriodoEvaluacion> findByEstadoAndInicioLessThanEqualOrderByAnioEscolarIdAscNumeroAsc(EstadoPeriodo estado,
			LocalDate hoy);
}
