package pe.edu.virgenmaria.cuentasclaras.cobranza.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.AjusteCuota;

import java.math.BigDecimal;

/** Libro de descuentos del colegio actual ({@code @TenantId}). SOLO INSERCIÓN. */
public interface AjusteCuotaRepository extends Repository<AjusteCuota, Long> {

	AjusteCuota save(AjusteCuota ajuste);

	/** Lo descontado de una cuota según el libro. {@code null} si no tiene ajustes. */
	@Query("select sum(a.monto) from AjusteCuota a where a.cuota.id = :cuota")
	BigDecimal sumaDeCuota(@Param("cuota") Long cuotaId);

	long countByDescuentoId(Long descuentoId);

	/**
	 * Sprint 6: descuentos APROBADOS en un rango de momentos [desde, hasta), por quien aprobó: aprobador, cuántos
	 * descuentos y lo que de verdad se descontó (suma del libro de ajustes, no el total estimado).
	 */
	@Query("select d.resueltoPor, count(distinct d.id), sum(a.monto) from AjusteCuota a join a.descuento d "
			+ "where d.resueltoEn >= :desde and d.resueltoEn < :hasta group by d.resueltoPor")
	java.util.List<Object[]> aprobadosEntre(@Param("desde") java.time.LocalDateTime desde,
			@Param("hasta") java.time.LocalDateTime hasta);
}
