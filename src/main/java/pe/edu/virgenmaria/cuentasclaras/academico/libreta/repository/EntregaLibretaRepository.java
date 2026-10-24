package pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.EntregaLibreta;

import java.util.Collection;
import java.util.List;

/** Sprint 10, tanda 3: las constancias de entrega. SOLO INSERCIÓN: sin cambios ni {@code delete*}. */
public interface EntregaLibretaRepository extends Repository<EntregaLibreta, Long> {

	EntregaLibreta saveAndFlush(EntregaLibreta entrega);

	List<EntregaLibreta> findByLibretaIdInOrderByIdAsc(Collection<Long> libretas);

	long countByLibretaId(Long libretaId);
}
