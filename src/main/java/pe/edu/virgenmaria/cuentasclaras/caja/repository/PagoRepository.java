package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;

import java.util.List;
import java.util.Optional;

/** Pagos del colegio actual ({@code @TenantId}). Libro de solo inserción: sin borrados ni {@code @Modifying}. */
public interface PagoRepository extends Repository<Pago, Long> {

	Pago save(Pago pago);

	Optional<Pago> findById(Long id);

	Optional<Pago> findByClaveIdempotencia(String clave);

	/** Un número de operación digital vigente no se registra dos veces (también es UNIQUE en la base). */
	boolean existsByMedioAndOperacionVigente(MedioPago medio, String operacionVigente);

	List<Pago> findByCajaIdOrderByIdDesc(Long cajaId);

	List<Pago> findTop5ByFamiliaIdOrderByIdDesc(Long familiaId);

	Pago saveAndFlush(Pago pago);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Pago p where p.id = :id")
	Optional<Pago> bloquear(@Param("id") Long id);

	/** La caja del pago, sin cargarlo (para bloquear la caja ANTES que el pago). */
	@Query("select p.caja.id from Pago p where p.id = :id")
	Optional<Long> cajaDe(@Param("id") Long pagoId);

	/** Efectivo VIGENTE de una caja (lo que la cajera debe tener, sin el fondo). {@code null} si no hay. */
	@Query("select sum(p.total) from Pago p where p.caja.id = :caja "
			+ "and p.medio = pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO "
			+ "and p.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE")
	java.math.BigDecimal efectivoVigente(@Param("caja") Long cajaId);

	Optional<Pago> findByComprobanteId(Long comprobanteId);

	List<Pago> findByIdIn(java.util.Collection<Long> ids);

	/** Pagos VIGENTES de una caja por medio (los digitales nunca entran al esperado del cierre). */
	@Query("select p.medio, count(p), sum(p.total) from Pago p where p.caja.id = :caja "
			+ "and p.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE group by p.medio")
	List<Object[]> vigentesPorMedio(@Param("caja") Long cajaId);

	/** Pagos de un día de todas las cajas (resumen de Promotoría). */
	List<Pago> findByFechaOrderByIdAsc(java.time.LocalDate fecha);

	/** Pagos digitales VIGENTES que Administración aún no comparó con el banco, del más antiguo al más nuevo. */
	@Query("select p from Pago p where p.medio <> pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO "
			+ "and p.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE "
			+ "and not exists (select v.id from VerificacionBancaria v where v.pago = p) order by p.fecha, p.id")
	List<Pago> digitalesSinVerificar();
}
