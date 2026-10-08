package pe.edu.virgenmaria.cuentasclaras.pasarela.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Órdenes de pago en línea del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface OrdenPagoRepository extends Repository<OrdenPago, Long> {

	OrdenPago save(OrdenPago orden);

	OrdenPago saveAndFlush(OrdenPago orden);

	Optional<OrdenPago> findById(Long id);

	Optional<OrdenPago> findByReferencia(String referencia);

	Optional<OrdenPago> findByClaveIdempotencia(String clave);

	Optional<OrdenPago> findByProveedorOrdenId(String proveedorOrdenId);

	/** La orden cobrada con esa operación canónica (para la línea CONTRACARGO de una liquidación). */
	Optional<OrdenPago> findFirstByOperacionOrderByIdDesc(String operacion);

	/** La orden bloqueada (primer bloqueo del procesamiento de un pago en línea). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from OrdenPago o where o.id = :id")
	Optional<OrdenPago> bloquear(@Param("id") Long id);

	List<OrdenPago> findTop10ByFamiliaIdOrderByIdDesc(Long familiaId);

	List<OrdenPago> findByEstadoOrderByIdAsc(EstadoOrden estado);

	List<OrdenPago> findByEstadoInOrderByIdDesc(Collection<EstadoOrden> estados);

	List<OrdenPago> findTop50ByOrderByIdDesc();

	/** Órdenes en curso (para consultar a la pasarela o vencerlas). */
	List<OrdenPago> findByEstadoAndCreadoEnBeforeOrderByIdAsc(EstadoOrden estado, LocalDateTime creadaAntes);

	/** Órdenes vencidas recientes (por si llega un pago tardío). */
	List<OrdenPago> findByEstadoAndVenceEnAfterOrderByIdAsc(EstadoOrden estado, LocalDateTime desde);

	/** Órdenes en curso y no vencidas que llevan alguna de estas cuotas. */
	@Query("select distinct x.orden from OrdenPagoCuota x where x.cuota.id in :cuotas "
			+ "and x.orden.estado = pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden.CREADA "
			+ "and x.orden.venceEn > :ahora")
	List<OrdenPago> abiertasConCuotas(@Param("cuotas") Collection<Long> cuotas, @Param("ahora") LocalDateTime ahora);

	long countByProveedor(pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela proveedor);
}
