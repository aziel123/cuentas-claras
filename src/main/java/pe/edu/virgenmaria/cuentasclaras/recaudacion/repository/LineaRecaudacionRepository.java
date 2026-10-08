package pe.edu.virgenmaria.cuentasclaras.recaudacion.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Líneas de recaudación del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface LineaRecaudacionRepository extends Repository<LineaRecaudacion, Long> {

	LineaRecaudacion save(LineaRecaudacion linea);

	Optional<LineaRecaudacion> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LineaRecaudacion l where l.id = :id")
	Optional<LineaRecaudacion> bloquear(@Param("id") Long id);

	List<LineaRecaudacion> findByLoteIdOrderByNumeroAsc(Long loteId);

	List<LineaRecaudacion> findByIdInOrderByNumeroAsc(Collection<Long> ids);

	/** Ids de las líneas que faltan aplicar, en el orden del archivo. */
	@Query("select l.id from LineaRecaudacion l where l.lote.id = :lote "
			+ "and l.estado = pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea.PENDIENTE order by l.numero")
	List<Long> pendientesDe(@Param("lote") Long loteId);

	/** Fechas de pago de las líneas que faltan aplicar (una caja de canal por fecha). */
	@Query("select distinct l.fechaPago from LineaRecaudacion l where l.lote.id = :lote "
			+ "and l.estado = pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea.PENDIENTE order by l.fechaPago")
	List<LocalDate> fechasPendientesDe(@Param("lote") Long loteId);

	List<LineaRecaudacion> findByEstadoOrderByIdAsc(EstadoLinea estado);

	long countByEstado(EstadoLinea estado);

	/** Líneas cuyo número de operación ya está en otra línea vigente (otro archivo con el mismo pago). */
	@Query("select count(l) from LineaRecaudacion l where l.numeroOperacion = :operacion "
			+ "and l.lote.estado in (pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLote.CARGADO, "
			+ "pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLote.CONFIRMADO)"
			+ " and l.estado = pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea.PENDIENTE")
	long pendientesConOperacion(@Param("operacion") String operacion);

	/** S4-A4: las líneas devueltas en un rango (salen del banco como cargo: la conciliación espera verlas). */
	List<LineaRecaudacion> findByEstadoAndDevueltoEnBetweenOrderByIdAsc(EstadoLinea estado, java.time.LocalDateTime desde,
			java.time.LocalDateTime hasta);
}
