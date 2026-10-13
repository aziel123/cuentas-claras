package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PrimeraPromotoria;

import java.util.Optional;

/** La primera Promotoría de cada colegio (sprint 8, tanda 1): solo inserción; la filtra {@code @TenantId}. */
public interface PrimeraPromotoriaRepository extends Repository<PrimeraPromotoria, Long> {

	PrimeraPromotoria saveAndFlush(PrimeraPromotoria primera);

	/** Si el colegio en contexto ya tiene su primera Promotoría. */
	boolean existsByIdNotNull();

	Optional<PrimeraPromotoria> findFirstByIdNotNull();

	/** Si esa cuenta (del colegio en contexto) es el titular de la primera Promotoría. */
	Optional<PrimeraPromotoria> findByUsuarioId(Long usuarioId);
}
