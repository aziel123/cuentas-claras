package pe.edu.virgenmaria.cuentasclaras.recaudacion.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LoteRecaudacion;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Lotes de recaudación del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface LoteRecaudacionRepository extends Repository<LoteRecaudacion, Long> {

	LoteRecaudacion save(LoteRecaudacion lote);

	LoteRecaudacion saveAndFlush(LoteRecaudacion lote);

	Optional<LoteRecaudacion> findById(Long id);

	/** El lote bloqueado (primer bloqueo de la confirmación y de la aplicación de sus pagos). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LoteRecaudacion l where l.id = :id")
	Optional<LoteRecaudacion> bloquear(@Param("id") Long id);

	/** El lote vigente (CARGADO, CONFIRMADO o APLICADO) de ese archivo, si lo hay. */
	Optional<LoteRecaudacion> findByShaVigente(String sha256);

	List<LoteRecaudacion> findTop50ByOrderByIdDesc();

	List<LoteRecaudacion> findByEstadoOrderByIdAsc(EstadoLote estado);

	List<LoteRecaudacion> findByEstadoAndRechazadoEnAfterOrderByIdDesc(EstadoLote estado, LocalDateTime desde);

	/** Lotes ya confirmados (CONFIRMADO o APLICADO) de un rango de fechas de proceso: su abono debe verse en el extracto. */
	List<LoteRecaudacion> findByEstadoInAndFechaProcesoBetweenOrderByIdAsc(java.util.Collection<EstadoLote> estados,
			java.time.LocalDate desde, java.time.LocalDate hasta);

	List<LoteRecaudacion> findByIdIn(java.util.Collection<Long> ids);

	/**
	 * S4-A1: si hay un lote en ese estado cuyas fechas se superponen con {@code desde}..{@code hasta} (para no dejar
	 * descargar el archivo de un lote descartado o rechazado mientras otro de esos días espera su confirmación a ciegas).
	 */
	boolean existsByEstadoAndDesdeLessThanEqualAndHastaGreaterThanEqual(EstadoLote estado, LocalDate hasta,
			LocalDate desde);
}
