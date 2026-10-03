package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/** Libro de aplicaciones del colegio actual ({@code @TenantId}). SOLO INSERCIÓN: sin borrados ni {@code @Modifying}. */
public interface AplicacionPagoRepository extends Repository<AplicacionPago, Long> {

	AplicacionPago save(AplicacionPago aplicacion);

	/** Lo pagado de una cuota según el libro. {@code null} si no tiene aplicaciones. */
	@Query("select sum(a.monto) from AplicacionPago a where a.cuota.id = :cuota")
	BigDecimal sumaDeCuota(@Param("cuota") Long cuotaId);

	@Query("select a from AplicacionPago a join fetch a.cuota c join fetch c.alumno where a.pago.id in :pagos "
			+ "order by a.pago.id, a.id")
	List<AplicacionPago> dePagos(@Param("pagos") Collection<Long> pagoIds);

	/** Las aplicaciones originales (no reversiones) de un pago, en orden. */
	List<AplicacionPago> findByPagoIdAndTipoOrderByIdAsc(Long pagoId, pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion tipo);

	/** Las cuotas a las que se aplicó un pago, sin cargarlas (para bloquearlas primero). */
	@Query("select a.cuota.id from AplicacionPago a where a.pago.id = :pago "
			+ "and a.tipo = pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion.APLICACION")
	List<Long> cuotasDePago(@Param("pago") Long pagoId);

	/**
	 * Si OTRO pago vigente está aplicado a alguna de esas cuotas (A2: una devolución por «pago duplicado» exige que el
	 * pago repetido exista).
	 */
	@Query("select count(a) > 0 from AplicacionPago a where a.cuota.id in :cuotas and a.pago.id <> :pago "
			+ "and a.tipo = pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion.APLICACION "
			+ "and a.pago.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE")
	boolean otroPagoVigenteDe(@Param("pago") Long pagoId, @Param("cuotas") Collection<Long> cuotaIds);

	/** Los pagos que tocaron cuotas de un alumno (aplicaciones y reversiones). */
	@Query("select distinct a.pago.id from AplicacionPago a where a.cuota.alumno.id = :alumno")
	List<Long> pagosDeAlumno(@Param("alumno") Long alumnoId);
}
