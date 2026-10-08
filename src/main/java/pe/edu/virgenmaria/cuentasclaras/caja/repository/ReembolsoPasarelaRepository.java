package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ReembolsoPasarela;

import java.util.Collection;
import java.util.List;

/** Reembolsos por la pasarela del colegio actual ({@code @TenantId}). Solo inserción: sin borrados ni {@code @Modifying}. */
public interface ReembolsoPasarelaRepository extends Repository<ReembolsoPasarela, Long> {

	ReembolsoPasarela save(ReembolsoPasarela reembolso);

	boolean existsByAnulacionId(Long anulacionId);

	List<ReembolsoPasarela> findByAnulacionIdIn(Collection<Long> anulaciones);
}
