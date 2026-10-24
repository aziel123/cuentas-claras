package pe.edu.virgenmaria.cuentasclaras.academico.notas.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.BorradorConclusion;

import java.util.Optional;

/** Sprint 10, tanda 2 (M8): los borradores de conclusión del colegio actual. SOLO INSERCIÓN: sin delete* ni @Modifying. */
public interface BorradorConclusionRepository extends Repository<BorradorConclusion, Long> {

	BorradorConclusion saveAndFlush(BorradorConclusion borrador);

	Optional<BorradorConclusion> findById(Long id);
}
