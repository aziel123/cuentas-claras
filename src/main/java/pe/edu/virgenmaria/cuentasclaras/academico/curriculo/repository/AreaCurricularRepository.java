package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.AreaCurricular;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Sprint 10, tanda 1: áreas del catálogo, solo lectura (sin save ni delete). */
public interface AreaCurricularRepository extends Repository<AreaCurricular, Long> {

	Optional<AreaCurricular> findById(Long id);

	List<AreaCurricular> findByIdIn(Collection<Long> ids);

	List<AreaCurricular> findByVersionIdOrderByNivelAscOrdenAsc(Long versionId);

	List<AreaCurricular> findByVersionIdAndNivelOrderByOrdenAsc(Long versionId, Nivel nivel);
}
