package pe.edu.virgenmaria.cuentasclaras.academico.notas.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.ExoneracionArea;

import java.util.Collection;
import java.util.List;

/** Sprint 10, tanda 2: las exoneraciones del colegio actual. SOLO INSERCIÓN: sin delete* ni @Modifying. */
public interface ExoneracionAreaRepository extends Repository<ExoneracionArea, Long> {

	ExoneracionArea saveAndFlush(ExoneracionArea exoneracion);

	boolean existsByMatriculaIdAndAreaId(Long matriculaId, Long areaId);

	List<ExoneracionArea> findByMatriculaIdIn(Collection<Long> matriculas);

	List<ExoneracionArea> findByAnioEscolarIdOrderByIdAsc(Long anioEscolarId);
}
