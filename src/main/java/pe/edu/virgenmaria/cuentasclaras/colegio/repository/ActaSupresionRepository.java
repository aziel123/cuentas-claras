package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.ActaSupresion;

import java.util.Optional;

/** Sprint 11, tanda 2: el acta de supresión del colegio en contexto (una por colegio). */
public interface ActaSupresionRepository extends JpaRepository<ActaSupresion, Long> {

	Optional<ActaSupresion> findFirstByIdNotNull();
}
