package pe.edu.virgenmaria.cuentasclaras.academico.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.model.RegistroAsistencia;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 9, tanda 2: registros de asistencia del colegio actual (los filtra {@code @TenantId}). SOLO INSERCIÓN: no
 * extiende CrudRepository (no hereda {@code delete*}), no tiene {@code @Modifying} y {@code saveAndFlush} solo se usa con
 * registros nuevos (la entidad rechaza un UPDATE: {@code InmutabilidadAcademicoTest}).
 */
public interface RegistroAsistenciaRepository extends Repository<RegistroAsistencia, Long> {

	RegistroAsistencia saveAndFlush(RegistroAsistencia registro);

	Optional<RegistroAsistencia> findById(Long id);

	Optional<RegistroAsistencia> findBySeccionIdAndFecha(Long seccionId, LocalDate fecha);

	List<RegistroAsistencia> findByFecha(LocalDate fecha);

	List<RegistroAsistencia> findBySeccionIdAndFechaBetweenOrderByFechaAsc(Long seccionId, LocalDate desde,
			LocalDate hasta);

	@Query("select r from RegistroAsistencia r where r.id in :ids")
	List<RegistroAsistencia> deIds(@Param("ids") Collection<Long> ids);
}
