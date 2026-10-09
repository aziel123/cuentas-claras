package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ResultadoVerificacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.VerificacionBancaria;

import java.util.Collection;
import java.util.List;

/** Verificaciones bancarias del colegio actual ({@code @TenantId}). Solo inserción. */
public interface VerificacionBancariaRepository extends Repository<VerificacionBancaria, Long> {

	VerificacionBancaria save(VerificacionBancaria verificacion);

	boolean existsByPagoId(Long pagoId);

	boolean existsByDepositoId(Long depositoId);

	/** Sprint 7, tanda 2: cuántas verificaciones tiene el pago (la clave de la firma de la siguiente). */
	long countByPagoId(Long pagoId);

	/** Sprint 7, tanda 2: cuántas verificaciones tiene el depósito (la clave de la firma de la siguiente). */
	long countByDepositoId(Long depositoId);

	List<VerificacionBancaria> findByPagoIdIn(Collection<Long> pagos);

	List<VerificacionBancaria> findByDepositoIdIn(Collection<Long> depositos);

	List<VerificacionBancaria> findByResultadoOrderByIdDesc(ResultadoVerificacion resultado);

	List<VerificacionBancaria> findTop30ByOrderByIdDesc();

	boolean existsByPagoIdAndResultado(Long pagoId, ResultadoVerificacion resultado);

	java.util.Optional<VerificacionBancaria> findByPagoId(Long pagoId);

	/** Verificaciones de un rango de tiempo (muestreo de Promotoría). */
	List<VerificacionBancaria> findByResultadoAndCreadoEnBetweenOrderByIdAsc(ResultadoVerificacion resultado,
			java.time.LocalDateTime desde, java.time.LocalDateTime hasta);
}
