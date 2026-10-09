package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.SesionUsuario;

import java.util.List;
import java.util.Optional;

/**
 * Sesiones de la base del colegio actual ({@code @TenantId}). Sin {@code delete*} ni {@code save} genérico de
 * {@code CrudRepository}: una sesión no se borra, se cierra (sprint 7, tanda 2).
 */
public interface SesionUsuarioRepository extends Repository<SesionUsuario, Long> {

	SesionUsuario saveAndFlush(SesionUsuario sesion);

	Optional<SesionUsuario> findById(Long id);

	/** Las abiertas de una persona, bloqueadas (un segundo ingreso cierra las anteriores sin carreras). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SesionUsuario s where s.usuarioId = :usuarioId and s.cerradaEn is null order by s.id")
	List<SesionUsuario> bloquearAbiertasDe(@Param("usuarioId") Long usuarioId);

	/** Todas las abiertas (al arrancar se cierran: las sesiones HTTP anteriores ya no existen). */
	@Query("select s from SesionUsuario s where s.cerradaEn is null order by s.id")
	List<SesionUsuario> abiertas();
}
