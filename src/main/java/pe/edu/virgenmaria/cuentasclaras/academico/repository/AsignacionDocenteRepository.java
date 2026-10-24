package pe.edu.virgenmaria.cuentasclaras.academico.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AsignacionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 9, tanda 1: asignaciones docentes del colegio actual (las filtra {@code @TenantId}). Dan acceso a los datos de
 * menores: no extiende CrudRepository (no hereda {@code delete*}) ni tiene {@code @Modifying}.
 */
public interface AsignacionDocenteRepository extends Repository<AsignacionDocente, Long> {

	AsignacionDocente save(AsignacionDocente asignacion);

	AsignacionDocente saveAndFlush(AsignacionDocente asignacion);

	Optional<AsignacionDocente> findById(Long id);

	/** Lee y bloquea la asignación: una aprobación y una terminación (o dos) a la vez se serializan. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AsignacionDocente a where a.id = :id")
	Optional<AsignacionDocente> bloquear(@Param("id") Long id);

	/** Las de un docente en ese estado (sus secciones vigentes). */
	List<AsignacionDocente> findByUsuarioIdAndEstadoOrderBySeccionIdAsc(Long usuarioId, EstadoAsignacion estado);

	/** Si el docente tiene una asignación en ese estado en la sección (de cualquier tipo, o del tipo). */
	boolean existsByUsuarioIdAndSeccionIdAndEstado(Long usuarioId, Long seccionId, EstadoAsignacion estado);

	boolean existsByUsuarioIdAndSeccionIdAndTipoAndEstadoIn(Long usuarioId, Long seccionId, TipoAsignacion tipo,
			Collection<EstadoAsignacion> estados);

	/** El tutor vigente de la sección (uno como máximo: {@code uk_asignacion_docente_tutor}). */
	Optional<AsignacionDocente> findFirstBySeccionIdAndTipoAndEstado(Long seccionId, TipoAsignacion tipo,
			EstadoAsignacion estado);

	/** Sprint 10, tanda 1: la de ese docente, sección y tipo en ese estado (la VIGENTE que reemplaza una propuesta nueva). */
	Optional<AsignacionDocente> findFirstByUsuarioIdAndSeccionIdAndTipoAndEstado(Long usuarioId, Long seccionId,
			TipoAsignacion tipo, EstadoAsignacion estado);

	/** Todas las de un año (la pantalla de asignaciones), en orden de sección y de llegada. */
	List<AsignacionDocente> findByAnioEscolarIdOrderBySeccionIdAscIdAsc(Long anioEscolarId);

	/** Las de un año en esos estados (copiar las del año anterior: las vigentes o terminadas al cerrar). */
	List<AsignacionDocente> findByAnioEscolarIdAndEstadoInOrderBySeccionIdAscIdAsc(Long anioEscolarId,
			Collection<EstadoAsignacion> estados);

	/** Las vigentes del colegio (el proceso que termina las de cuentas desactivadas o años cerrados). */
	List<AsignacionDocente> findByEstadoOrderByIdAsc(EstadoAsignacion estado);
}
