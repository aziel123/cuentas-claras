package pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 10, tanda 3: las libretas emitidas del colegio actual (las filtra @TenantId). SOLO INSERCIÓN: sin delete* ni
 * {@code @Modifying}. La vigente de una matrícula y un periodo es la de versión mayor (la que nadie reemplaza).
 */
public interface LibretaEmitidaRepository extends Repository<LibretaEmitida, Long> {

	LibretaEmitida saveAndFlush(LibretaEmitida libreta);

	Optional<LibretaEmitida> findById(Long id);

	/** Todas las versiones de un periodo y una sección (la emisión decide qué falta). */
	List<LibretaEmitida> findByPeriodoIdAndSeccionIdOrderByMatriculaIdAscNumeroVersionAsc(Long periodoId, Long seccionId);

	/** Todas las versiones de un periodo (la integridad y el cierre). */
	List<LibretaEmitida> findByPeriodoIdOrderByIdAsc(Long periodoId);

	/** La vigente de una matrícula en un periodo. */
	Optional<LibretaEmitida> findFirstByPeriodoIdAndMatriculaIdOrderByNumeroVersionDesc(Long periodoId, Long matriculaId);

	/** Las vigentes de un periodo y una sección (la impresión y la exportación para el SIAGIE). */
	@Query("select l from LibretaEmitida l where l.periodoId = :periodo and l.seccionId = :seccion and not exists "
			+ "(select 1 from LibretaEmitida x where x.reemplazaId = l.id) order by l.id asc")
	List<LibretaEmitida> vigentesDeSeccion(@Param("periodo") Long periodoId, @Param("seccion") Long seccionId);

	/**
	 * QA del sprint 10: las vigentes de esas matrículas en un periodo, en la sección que sea (la exportación para el SIAGIE:
	 * un alumno trasladado después del cierre tiene su libreta en la sección anterior).
	 */
	@Query("select l from LibretaEmitida l where l.periodoId = :periodo and l.matriculaId in :matriculas and not exists "
			+ "(select 1 from LibretaEmitida x where x.reemplazaId = l.id) order by l.id asc")
	List<LibretaEmitida> vigentesDeMatriculas(@Param("periodo") Long periodoId,
			@Param("matriculas") Collection<Long> matriculas);

	/** Todas las versiones de unos alumnos (el portal de la familia: nada desaparece). */
	List<LibretaEmitida> findByAlumnoIdInOrderByPeriodoIdDescAlumnoIdAscNumeroVersionDesc(Collection<Long> alumnos);

	/** Las emitidas desde una hora (los avisos a las familias). */
	List<LibretaEmitida> findByCreadoEnGreaterThanEqualOrderByIdAsc(LocalDateTime desde);

	/** La que emitió una reemisión aprobada (una por solicitud: uk_libreta_emitida_solicitud). */
	boolean existsBySolicitudId(Long solicitudId);

	/** Cuántas hay en el colegio (la demo y las pruebas). */
	long count();
}
