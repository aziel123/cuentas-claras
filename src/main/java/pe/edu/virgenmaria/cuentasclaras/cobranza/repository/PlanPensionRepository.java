package pe.edu.virgenmaria.cuentasclaras.cobranza.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoPlan;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

import java.util.List;
import java.util.Optional;

/**
 * Planes de pensiones del colegio actual (los filtra {@code @TenantId}). Tabla financiera: el repositorio no ofrece
 * ningún borrado (no extiende CrudRepository) y no tiene {@code @Modifying} (regla ArchUnit).
 */
public interface PlanPensionRepository extends Repository<PlanPension, Long> {

	PlanPension save(PlanPension plan);

	PlanPension saveAndFlush(PlanPension plan);

	Optional<PlanPension> findById(Long id);

	List<PlanPension> findByAnioEscolarIdOrderByNivelAscNumeroVersionDesc(Long anioId);

	List<PlanPension> findByAnioEscolarIdAndNivelOrderByNumeroVersionDesc(Long anioId, Nivel nivel);

	Optional<PlanPension> findByAnioEscolarIdAndNivelAndVigenteTrue(Long anioId, Nivel nivel);

	List<PlanPension> findByAnioEscolarIdAndVigenteTrue(Long anioId);

	@Query("select p.anioEscolar.id from PlanPension p where p.id = :id")
	Optional<Long> anioDe(@Param("id") Long id);

	/** Lee el plan con bloqueo: dos aprobaciones simultáneas se serializan y la segunda ve el estado nuevo. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PlanPension p where p.id = :id")
	Optional<PlanPension> bloquear(@Param("id") Long id);

	boolean existsByAnioEscolarIdAndNivelAndEstado(Long anioId, Nivel nivel, EstadoPlan estado);

	@Query("select coalesce(max(p.numeroVersion), 0) from PlanPension p "
			+ "where p.anioEscolar.id = :anio and p.nivel = :nivel")
	int ultimaVersion(@Param("anio") Long anioId, @Param("nivel") Nivel nivel);

	/** El plan vigente más reciente del nivel en años anteriores: sirve de propuesta para el año nuevo. */
	@Query("select p from PlanPension p where p.nivel = :nivel and p.vigente = true and p.anioEscolar.anio < :anio "
			+ "order by p.anioEscolar.anio desc")
	List<PlanPension> vigentesAnteriores(@Param("nivel") Nivel nivel, @Param("anio") int anio);
}
