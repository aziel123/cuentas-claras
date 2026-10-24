package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AmbitoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.CambioEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 11, tanda 1: los cambios de estado del colegio del contexto ({@code @TenantId}). Sin borrados ni {@code update}
 * masivos: cada cambio pasa de estado con su entidad (y en MySQL lo vigila trg_cambio_estado_colegio_estado). Solo los
 * escribe {@code plataforma} (regla ArchUnit).
 */
public interface CambioEstadoColegioRepository extends Repository<CambioEstadoColegio, Long> {

	CambioEstadoColegio saveAndFlush(CambioEstadoColegio cambio);

	Optional<CambioEstadoColegio> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CambioEstadoColegio c where c.id = :id")
	Optional<CambioEstadoColegio> bloquear(@Param("id") Long id);

	/** Cuántos cambios tuvo el colegio: la firma de quien propone lleva el siguiente número. */
	long count();

	/** El pendiente (PROPUESTO o APROBADO) de un ámbito: uno a la vez (UNIQUE). */
	Optional<CambioEstadoColegio> findFirstByAmbitoAndEstadoIn(AmbitoCambioEstado ambito,
			Collection<EstadoCambioEstado> estados);

	/** Los pendientes del colegio (para la bandeja del operador y la franja del personal). */
	List<CambioEstadoColegio> findByEstadoInOrderByIdAsc(Collection<EstadoCambioEstado> estados);

	/** Los aprobados cuya fecha efectiva ya llegó (los aplica sistema.plataforma). */
	List<CambioEstadoColegio> findByEstadoAndEfectivaDesdeLessThanEqualOrderByIdAsc(EstadoCambioEstado estado,
			LocalDateTime ahora);

	/** Los aprobados de un tipo (la suspensión próxima: recordatorios y franja). */
	List<CambioEstadoColegio> findByTipoAndEstadoOrderByIdAsc(TipoCambioEstado tipo, EstadoCambioEstado estado);

	/** La historia del colegio, del más nuevo al más viejo. */
	List<CambioEstadoColegio> findAllByOrderByIdDesc();
}
