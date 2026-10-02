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
}
