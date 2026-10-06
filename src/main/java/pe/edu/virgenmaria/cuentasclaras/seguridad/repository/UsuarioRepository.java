package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.util.List;
import java.util.Optional;

/**
 * Usuarios. Todas las consultas las filtra Hibernate por el colegio actual ({@code @TenantId});
 * por eso aquí no se permiten consultas nativas (regla ArchUnit).
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByNombreUsuario(String nombreUsuario);

	/** Lee y bloquea la fila del usuario para actualizar su contador de intentos sin carreras. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Usuario u where u.nombreUsuario = :nombreUsuario")
	Optional<Usuario> bloquearPorNombreUsuario(@Param("nombreUsuario") String nombreUsuario);

	/** Lee y bloquea la fila del usuario por id (cambio de clave: el contador de intentos se actualiza sin carreras). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Usuario u where u.id = :id")
	Optional<Usuario> bloquearPorId(@Param("id") Long id);

	/**
	 * Usuarios activos con el rol, con sus filas bloqueadas: dos cambios simultáneos no pueden dejar al colegio
	 * sin ese rol (por ejemplo, sin Promotoría).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Usuario u where u.activo = true and :rol member of u.roles")
	List<Usuario> bloquearActivosConRol(@Param("rol") Rol rol);

	@Query("select count(distinct u) from Usuario u join u.roles r where u.activo = true and r = :rol")
	long contarActivosConRol(@Param("rol") Rol rol);

	List<Usuario> findAllByOrderByNombreCompletoAsc();

	/** Sprint 4: la cuenta en línea de un apoderado (como máximo una: UNIQUE). */
	Optional<Usuario> findByApoderadoId(Long apoderadoId);
}
