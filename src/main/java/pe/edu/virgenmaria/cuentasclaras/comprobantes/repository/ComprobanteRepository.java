package pe.edu.virgenmaria.cuentasclaras.comprobantes.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;

import java.util.Optional;

/** Comprobantes del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface ComprobanteRepository extends Repository<Comprobante, Long> {

	Comprobante save(Comprobante comprobante);

	Optional<Comprobante> findById(Long id);

	/** Cuántos comprobantes tiene la serie: debe ser igual a su último número (si no, hay un hueco). */
	long countBySerie(String serie);
}
