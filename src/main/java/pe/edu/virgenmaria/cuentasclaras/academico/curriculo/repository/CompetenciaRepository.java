package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.Competencia;

import java.util.Collection;
import java.util.List;

/** Sprint 10, tanda 1: competencias del catálogo, solo lectura (sin save ni delete). */
public interface CompetenciaRepository extends Repository<Competencia, Long> {

	List<Competencia> findByVersionIdOrderByCodigoAsc(Long versionId);

	List<Competencia> findByAreaIdInOrderByOrdenAsc(Collection<Long> areaIds);
}
