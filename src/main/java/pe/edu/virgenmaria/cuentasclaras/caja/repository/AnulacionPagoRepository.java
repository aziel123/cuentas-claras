package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AnulacionPago;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Anulaciones aprobadas del colegio actual ({@code @TenantId}). SOLO INSERCIÓN. */
public interface AnulacionPagoRepository extends Repository<AnulacionPago, Long> {

	AnulacionPago save(AnulacionPago anulacion);

	Optional<AnulacionPago> findById(Long id);

	Optional<AnulacionPago> findByPagoId(Long pagoId);

	List<AnulacionPago> findByPagoIdIn(Collection<Long> pagoIds);

	/** Si la nota de crédito es de una anulación de ese tipo (la de una devolución lleva espacio para la firma). */
	boolean existsByNotaCreditoIdAndTipo(Long notaCreditoId, pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion tipo);

	/**
	 * Devoluciones aprobadas cuyo reembolso Administración aún no registra (alerta crítica): ni a mano (efectivo o
	 * transferencia) ni por la pasarela (S4-A3). Un contracargo no es una devolución: no espera reembolso.
	 */
	@org.springframework.data.jpa.repository.Query("select a from AnulacionPago a where "
			+ "a.tipo = pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion.DEVOLUCION and not exists "
			+ "(select r.id from Reembolso r where r.anulacion = a) and not exists "
			+ "(select x.id from ReembolsoPasarela x where x.anulacion = a) order by a.id")
	List<AnulacionPago> devolucionesSinReembolso();

	/** Anulaciones aprobadas desde un momento (alerta diaria de devoluciones en efectivo). */
	List<AnulacionPago> findByCreadoEnGreaterThanEqualOrderByIdAsc(java.time.LocalDateTime desde);

	// --- Sprint 6, tanda 1 ---

	/** Anulaciones APROBADAS en un rango de momentos [desde, hasta): cantidad y suma (lo anulado del periodo). */
	@org.springframework.data.jpa.repository.Query("select count(a), sum(a.monto) from AnulacionPago a "
			+ "where a.creadoEn >= :desde and a.creadoEn < :hasta")
	List<Object[]> aprobadasEntre(@org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde,
			@org.springframework.data.repository.query.Param("hasta") java.time.LocalDateTime hasta);

	/** Nota de crédito de los pagos de un rango de días de caja que se anularon: id del pago, fecha, serie y número. */
	@org.springframework.data.jpa.repository.Query("select a.pago.id, n.fechaEmision, n.serie, n.numero "
			+ "from AnulacionPago a join a.notaCredito n join a.pago p where p.fecha between :desde and :hasta")
	List<Object[]> notasDePagosEntre(@org.springframework.data.repository.query.Param("desde") java.time.LocalDate desde,
			@org.springframework.data.repository.query.Param("hasta") java.time.LocalDate hasta);

	// --- Sprint 6, tanda 2 ---

	/**
	 * Recálculo del resumen diario (P4): pagos de un rango de días de caja anulados DESPUÉS de un momento, como fecha del
	 * pago, medio, total del pago, cuándo se registró el pago y cuándo se aprobó la anulación.
	 */
	@org.springframework.data.jpa.repository.Query("select p.fecha, p.medio, p.total, p.creadoEn, a.creadoEn "
			+ "from AnulacionPago a join a.pago p where p.fecha between :desde and :hasta and a.creadoEn > :despues")
	List<Object[]> anuladasDespuesDe(@org.springframework.data.repository.query.Param("desde") java.time.LocalDate desde,
			@org.springframework.data.repository.query.Param("hasta") java.time.LocalDate hasta,
			@org.springframework.data.repository.query.Param("despues") java.time.LocalDateTime despues);
}
