package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCierre;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Cierres de caja del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface CierreCajaRepository extends Repository<CierreCaja, Long> {

	CierreCaja save(CierreCaja cierre);

	CierreCaja saveAndFlush(CierreCaja cierre);

	Optional<CierreCaja> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CierreCaja c where c.id = :id")
	Optional<CierreCaja> bloquear(@Param("id") Long id);

	List<CierreCaja> findByEstadoOrderByIdAsc(EstadoCierre estado);

	List<CierreCaja> findByCajaIdOrderByNumeroAsc(Long cajaId);

	Optional<CierreCaja> findFirstByCajaIdOrderByNumeroDesc(Long cajaId);

	List<CierreCaja> findByCajaIdInOrderByNumeroAsc(Collection<Long> cajas);

	/**
	 * Correcciones del sprint 6 (S6-M3): los cierres de caja con diferencia creados en [desde, hasta): cuántos y la suma
	 * de sus diferencias (para el resumen diario: «desde el resumen anterior»).
	 */
	@Query("select count(c), sum(c.diferencia) from CierreCaja c where c.diferencia <> 0 and c.creadoEn >= :desde "
			+ "and c.creadoEn < :hasta")
	List<Object[]> cierresConDiferenciaEntre(@Param("desde") java.time.LocalDateTime desde,
			@Param("hasta") java.time.LocalDateTime hasta);
}
