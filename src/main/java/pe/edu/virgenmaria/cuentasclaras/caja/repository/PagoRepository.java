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

	/**
	 * Un número de operación digital vigente (forma canónica) no se registra dos veces, en ningún medio digital (también
	 * es UNIQUE en la base: uk_pago_operacion_canonica).
	 */
	boolean existsByOperacionVigente(String operacionVigente);

	/** Pagos digitales desde una fecha (para marcar números de operación parecidos en la conciliación). */
	@Query("select p from Pago p where p.medio <> pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO "
			+ "and p.fecha >= :desde")
	List<Pago> digitalesDesde(@Param("desde") java.time.LocalDate desde);

	List<Pago> findByCajaIdOrderByIdDesc(Long cajaId);

	List<Pago> findTop5ByFamiliaIdOrderByIdDesc(Long familiaId);

	Pago saveAndFlush(Pago pago);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Pago p where p.id = :id")
	Optional<Pago> bloquear(@Param("id") Long id);

	/** La caja del pago, sin cargarlo (para bloquear la caja ANTES que el pago). */
	@Query("select p.caja.id from Pago p where p.id = :id")
	Optional<Long> cajaDe(@Param("id") Long pagoId);


	Optional<Pago> findByComprobanteId(Long comprobanteId);

	/** El pago de una orden en línea (como máximo uno: UNIQUE). */
	Optional<Pago> findByOrdenPagoId(Long ordenPagoId);

	List<Pago> findByOrdenPagoIdIn(java.util.Collection<Long> ordenes);

	/** El pago de una línea de recaudación (como máximo uno: UNIQUE). */
	Optional<Pago> findByLineaRecaudacionId(Long lineaRecaudacionId);

	List<Pago> findByLineaRecaudacionIdIn(java.util.Collection<Long> lineas);

	/** Pagos de una caja de canal en un rango de días (ingresos automáticos para Promotoría). */
	@Query("select p from Pago p where p.caja.canal = :canal and p.fecha = :fecha order by p.id")
	List<Pago> deCanalEnFecha(@Param("canal") pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja canal,
			@Param("fecha") java.time.LocalDate fecha);

	List<Pago> findByIdIn(java.util.Collection<Long> ids);

	/** Pagos VIGENTES de una caja por medio (los digitales nunca entran al esperado del cierre). */
	@Query("select p.medio, count(p), sum(p.total) from Pago p where p.caja.id = :caja "
			+ "and p.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE group by p.medio")
	List<Object[]> vigentesPorMedio(@Param("caja") Long cajaId);

	/** Control de consistencia (M1): una boleta o factura sin su pago es un comprobante fuera del libro. */
	@Query("select c from Comprobante c where c.tipo in (pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante"
			+ ".BOLETA, pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante.FACTURA) and not exists "
			+ "(select p.id from Pago p where p.comprobante = c)")
	List<pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante> comprobantesSinPago();

	/** Control de consistencia (M1): una nota de crédito sin su anulación aprobada. */
	@Query("select c from Comprobante c where c.tipo = pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante"
			+ ".NOTA_CREDITO and not exists (select a.id from AnulacionPago a where a.notaCredito = c)")
	List<pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante> notasSinAnulacion();

	/** Pagos de un día de todas las cajas (resumen de Promotoría). */
	List<Pago> findByFechaOrderByIdAsc(java.time.LocalDate fecha);

	/**
	 * Pagos digitales VIGENTES de ventanilla que Administración aún no comparó con el banco, del más antiguo al más nuevo.
	 * Los pagos en línea (caja de canal) no entran: los cubre la liquidación de la pasarela (sprint 4, tanda 3).
	 */
	@Query("select p from Pago p where p.medio <> pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO "
			+ "and p.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE "
			+ "and p.caja.canal = pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja.VENTANILLA "
			+ "and not exists (select v.id from VerificacionBancaria v where v.pago = p) order by p.fecha, p.id")
	List<Pago> digitalesSinVerificar();

	// --- Sprint 4, tanda 3: conciliación automática con el extracto ---

	/**
	 * Pagos digitales VIGENTES de ventanilla (Yape, Plin, transferencia, tarjeta) de un rango de fechas: los que deben
	 * verse uno por uno en el banco. Los de canal no entran: los cubren la liquidación de la pasarela y el lote de
	 * recaudación.
	 */
	@Query("select p from Pago p where p.medio <> pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.EFECTIVO "
			+ "and p.medio <> pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago.RECAUDACION_BANCARIA "
			+ "and p.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE "
			+ "and p.caja.canal = pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja.VENTANILLA "
			+ "and p.fecha between :desde and :hasta order by p.fecha, p.id")
	List<Pago> digitalesDeVentanillaEntre(@Param("desde") java.time.LocalDate desde,
			@Param("hasta") java.time.LocalDate hasta);

	/** Pagos VIGENTES de una caja de canal (pasarela o recaudación) de un rango de fechas. */
	@Query("select p from Pago p where p.caja.canal = :canal "
			+ "and p.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE "
			+ "and p.fecha between :desde and :hasta order by p.fecha, p.id")
	List<Pago> deCanalEntre(@Param("canal") pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja canal,
			@Param("desde") java.time.LocalDate desde, @Param("hasta") java.time.LocalDate hasta);

	/** El pago de ese origen con esa operación (canónica), si existe (la línea de una liquidación se ata a su pago). */
	Optional<Pago> findFirstByNumeroOperacionAndOrigenOrderByIdDesc(String numeroOperacion,
			pe.edu.virgenmaria.cuentasclaras.caja.model.OrigenPago origen);
}
