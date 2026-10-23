package pe.edu.virgenmaria.cuentasclaras.academico.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.model.CodigoModular;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

import java.util.List;
import java.util.Optional;

/** Sprint 9, tanda 3: los códigos modulares del colegio actual. Sin {@code delete*}. */
public interface CodigoModularRepository extends Repository<CodigoModular, Long> {

	CodigoModular saveAndFlush(CodigoModular codigo);

	Optional<CodigoModular> findByNivel(Nivel nivel);

	List<CodigoModular> findAllByOrderByNivelAsc();
}
