package pe.edu.virgenmaria.cuentasclaras.academico.notas.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.LoteCalificacion;

import java.util.Optional;

/** Sprint 10, tanda 2: los lotes de notas del colegio actual. SOLO INSERCIÓN: sin delete* ni @Modifying. */
public interface LoteCalificacionRepository extends Repository<LoteCalificacion, Long> {

	LoteCalificacion saveAndFlush(LoteCalificacion lote);

	Optional<LoteCalificacion> findById(Long id);

	/** La última secuencia del periodo, la sección y el área (0 si no hay lotes). */
	@Query("select coalesce(max(l.secuencia), 0) from LoteCalificacion l where l.periodoId = :periodo and l.seccionId = :seccion and l.areaId = :area")
	int ultimaSecuencia(@Param("periodo") Long periodoId, @Param("seccion") Long seccionId, @Param("area") Long areaId);

	boolean existsBySolicitudId(Long solicitudId);

	/** Correcciones del sprint 10 (hallazgo 1): los lotes de esos periodos (todas las secciones). */
	java.util.List<LoteCalificacion> findByPeriodoIdInOrderByIdAsc(java.util.Collection<Long> periodos);

	/** Correcciones del sprint 10 (hallazgo 1): los lotes de una sección en esos periodos. */
	java.util.List<LoteCalificacion> findBySeccionIdAndPeriodoIdInOrderByIdAsc(Long seccionId,
			java.util.Collection<Long> periodos);
}
