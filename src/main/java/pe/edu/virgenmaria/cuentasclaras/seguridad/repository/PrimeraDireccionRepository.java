package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PrimeraDireccion;

/** La primera Dirección de cada colegio (correcciones del sprint 7): solo inserción; la filtra {@code @TenantId}. */
public interface PrimeraDireccionRepository extends Repository<PrimeraDireccion, Long> {

	PrimeraDireccion saveAndFlush(PrimeraDireccion primera);

	/** Si el colegio en contexto ya usó su excepción de la primera Dirección. */
	boolean existsByIdNotNull();
}
