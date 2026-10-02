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

	List<VerificacionBancaria> findByPagoIdIn(Collection<Long> pagos);

	List<VerificacionBancaria> findByDepositoIdIn(Collection<Long> depositos);

	List<VerificacionBancaria> findByResultadoOrderByIdDesc(ResultadoVerificacion resultado);

	List<VerificacionBancaria> findTop30ByOrderByIdDesc();
}
