package pe.edu.virgenmaria.cuentasclaras.pasarela.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.LiquidacionLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.TipoLineaLiquidacion;

import java.util.Collection;
import java.util.List;

/** Líneas de las liquidaciones del colegio actual ({@code @TenantId}). Solo inserción. */
public interface LiquidacionLineaRepository extends Repository<LiquidacionLinea, Long> {

	LiquidacionLinea save(LiquidacionLinea linea);

	List<LiquidacionLinea> findByLiquidacionIdOrderByNumeroAsc(Long liquidacionId);

	List<LiquidacionLinea> findByLiquidacionIdIn(Collection<Long> liquidaciones);

	boolean existsByTipoAndOperacion(TipoLineaLiquidacion tipo, String operacion);

	/** Cargos que la pasarela liquidó y que no corresponden a ningún pago registrado (alerta crítica). */
	List<LiquidacionLinea> findByTipoAndPagoIdIsNullOrderByIdAsc(TipoLineaLiquidacion tipo);

	/** Los pagos (en línea) ya liquidados. */
	List<LiquidacionLinea> findByTipoAndPagoIdIn(TipoLineaLiquidacion tipo, Collection<Long> pagos);
}
