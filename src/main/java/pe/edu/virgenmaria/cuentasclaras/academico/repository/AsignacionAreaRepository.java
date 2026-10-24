package pe.edu.virgenmaria.cuentasclaras.academico.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionArea;

import java.util.Collection;
import java.util.List;

/**
 * Sprint 10, tanda 1 (A2): las áreas de cada asignación del colegio actual (las filtra {@code @TenantId}). SOLO INSERCIÓN:
 * sin {@code delete*} ni {@code @Modifying} (no extiende CrudRepository).
 */
public interface AsignacionAreaRepository extends Repository<AsignacionArea, Long> {

	AsignacionArea saveAndFlush(AsignacionArea area);

	List<AsignacionArea> findByAsignacionIdOrderByAreaIdAsc(Long asignacionId);

	List<AsignacionArea> findByAsignacionIdInOrderByAreaIdAsc(Collection<Long> asignacionIds);

	boolean existsByAsignacionId(Long asignacionId);
}
