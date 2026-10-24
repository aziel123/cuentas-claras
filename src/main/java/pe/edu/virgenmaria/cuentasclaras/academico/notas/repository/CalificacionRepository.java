package pe.edu.virgenmaria.cuentasclaras.academico.notas.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.model.Calificacion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10, tanda 2: las notas del colegio actual (las filtra @TenantId). SOLO INSERCIÓN: sin delete* ni @Modifying. La
 * vigente de cada cadena es la que nadie reemplaza.
 */
public interface CalificacionRepository extends Repository<Calificacion, Long> {

	Calificacion save(Calificacion calificacion);

	Calificacion saveAndFlush(Calificacion calificacion);

	Optional<Calificacion> findById(Long id);

	void flush();

	/** Las vigentes de una sección y un periodo en esas competencias. */
	@Query("select c from Calificacion c where c.periodoId = :periodo and c.seccionId = :seccion and c.competenciaId in :competencias and not exists (select 1 from Calificacion x where x.reemplazaId = c.id)")
	List<Calificacion> vigentes(@Param("periodo") Long periodoId, @Param("seccion") Long seccionId, @Param("competencias") Collection<Long> competencias);

	/**
	 * QA del sprint 10: las vigentes de esas matrículas en un periodo y esas competencias, sin mirar la sección donde se
	 * registraron. La cadena de una nota es de la matrícula (periodo, matrícula y competencia): un alumno trasladado a mitad
	 * de periodo conserva su nota y la nueva sección la continúa; un retirado o un exonerado no cuenta si no está en la lista.
	 */
	@Query("select c from Calificacion c where c.periodoId = :periodo and c.matriculaId in :matriculas and c.competenciaId in :competencias and not exists (select 1 from Calificacion x where x.reemplazaId = c.id)")
	List<Calificacion> vigentesDeMatriculas(@Param("periodo") Long periodoId, @Param("matriculas") Collection<Long> matriculas,
			@Param("competencias") Collection<Long> competencias);

	/** Las vigentes de un periodo (de todas las secciones), para el avance y el cierre. */
	@Query("select c from Calificacion c where c.periodoId = :periodo and not exists (select 1 from Calificacion x where x.reemplazaId = c.id)")
	List<Calificacion> vigentesDelPeriodo(@Param("periodo") Long periodoId);

	/** Las vigentes de una matrícula en un periodo para esas competencias (la cadena de la matrícula). */
	@Query("select c from Calificacion c where c.periodoId = :periodo and c.matriculaId = :matricula and c.competenciaId = :competencia and not exists (select 1 from Calificacion x where x.reemplazaId = c.id)")
	Optional<Calificacion> vigente(@Param("periodo") Long periodoId, @Param("matricula") Long matriculaId, @Param("competencia") Long competenciaId);

	boolean existsByReemplazaId(Long id);

	/** Correcciones del sprint 10 (hallazgo 1): las notas de esos lotes en el orden en que se registraron. */
	List<Calificacion> findByLoteIdInOrderByIdAsc(Collection<Long> lotes);

	/** Correcciones del sprint 10 (hallazgo 4): las corregidas o tardías de esos periodos (los anteriores del año). */
	List<Calificacion> findByPeriodoIdInAndOrigenInOrderByIdAsc(Collection<Long> periodos,
			Collection<pe.edu.virgenmaria.cuentasclaras.academico.notas.model.OrigenCalificacion> origenes);

	/** Las corregidas por solicitud en un periodo (la vigilancia de Dirección y la alerta A5). */
	List<Calificacion> findByPeriodoIdAndOrigenOrderByIdAsc(Long periodoId, pe.edu.virgenmaria.cuentasclaras.academico.notas.model.OrigenCalificacion origen);

	/** Sprint 10, tanda 3: toda la historia de unas matrículas en esos periodos (la foto de la libreta la filtra por id). */
	List<Calificacion> findByMatriculaIdInAndPeriodoIdInOrderByIdAsc(Collection<Long> matriculas, Collection<Long> periodos);

	/** Sprint 10, tanda 3: [matrícula, id más alto] de lo registrado hasta una hora (la marca de agua de la versión 1). */
	@Query("select c.matriculaId, max(c.id) from Calificacion c where c.matriculaId in :matriculas and c.registradoBd <= :hasta group by c.matriculaId")
	List<Object[]> ultimasHasta(@Param("matriculas") Collection<Long> matriculas, @Param("hasta") java.time.LocalDateTime hasta);

	/** Sprint 10, tanda 3: [matrícula, id más alto] de todo lo registrado (la marca de agua de una versión corregida). */
	@Query("select c.matriculaId, max(c.id) from Calificacion c where c.matriculaId in :matriculas group by c.matriculaId")
	List<Object[]> ultimas(@Param("matriculas") Collection<Long> matriculas);

	/** Sprint 10, tanda 3: las correcciones y notas tardías de un periodo (las que piden una libreta nueva). */
	List<Calificacion> findByPeriodoIdAndOrigenInOrderByIdAsc(Long periodoId,
			Collection<pe.edu.virgenmaria.cuentasclaras.academico.notas.model.OrigenCalificacion> origenes);

	List<Calificacion> findByOrigenAndCreadoEnGreaterThanEqualOrderByIdAsc(pe.edu.virgenmaria.cuentasclaras.academico.notas.model.OrigenCalificacion origen, java.time.LocalDateTime desde);
}
