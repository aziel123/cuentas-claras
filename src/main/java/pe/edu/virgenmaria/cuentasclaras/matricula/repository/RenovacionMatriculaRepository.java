package pe.edu.virgenmaria.cuentasclaras.matricula.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.EstadoRenovacion;
import pe.edu.virgenmaria.cuentasclaras.matricula.model.RenovacionMatricula;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Renovaciones del colegio actual (las filtra {@code @TenantId}). Sin {@code delete*}: una renovación vence o se responde,
 * nunca se borra.
 */
public interface RenovacionMatriculaRepository extends Repository<RenovacionMatricula, Long> {

	RenovacionMatricula save(RenovacionMatricula renovacion);

	RenovacionMatricula saveAndFlush(RenovacionMatricula renovacion);

	Optional<RenovacionMatricula> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RenovacionMatricula r where r.id = :id")
	Optional<RenovacionMatricula> bloquear(@Param("id") Long id);

	boolean existsByAlumnoIdAndAnioDestinoId(Long alumnoId, Long anioDestinoId);

	long countByAnioDestinoId(Long anioDestinoId);

	@Query("select r from RenovacionMatricula r join fetch r.alumno join fetch r.seccionDestino "
			+ "where r.anioDestino.id = :anio order by r.gradoDestino, r.alumno.nombreBusqueda, r.id")
	List<RenovacionMatricula> delAnio(@Param("anio") Long anioDestinoId);

	@Query("select r from RenovacionMatricula r join fetch r.alumno join fetch r.seccionDestino s join fetch r.anioDestino "
			+ "where r.familiaId = :familia order by r.anioDestino.anio desc, r.id")
	List<RenovacionMatricula> deFamilia(@Param("familia") Long familiaId);

	List<RenovacionMatricula> findByEstadoOrderByIdAsc(EstadoRenovacion estado);

	List<RenovacionMatricula> findByEstadoAndVenceEnBeforeOrderByIdAsc(EstadoRenovacion estado, LocalDate fecha);
}
