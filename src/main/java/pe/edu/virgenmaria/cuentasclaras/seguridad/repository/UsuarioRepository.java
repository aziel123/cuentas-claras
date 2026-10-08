package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContactoNormal;
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

	/** Sprint 6, tanda 3: las cuentas en línea de varios apoderados (quién usa el portal, para la llamada de control). */
	List<Usuario> findByApoderadoIdIn(java.util.Collection<Long> apoderadoIds);

	/** Sprint 5: los activos con un rol (sin bloquear), por ejemplo Promotoría para la huella diaria. */
	@Query("select u from Usuario u where u.activo = true and :rol member of u.roles order by u.id")
	List<Usuario> activosConRol(@Param("rol") Rol rol);

	/** Celulares y correos del personal activo (no de cuentas de apoderado), para compararlos normalizados. */
	@Query("select u from Usuario u where u.activo = true and u.apoderadoId is null")
	List<Usuario> personalActivo();

	/**
	 * Sprint 5 (G6; correcciones S5-A1): si un celular o correo es de alguien del personal activo (no de una cuenta de
	 * apoderado), comparando su forma NORMALIZADA ({@link ContactoNormal}): un alias de Gmail con «+» o con puntos es el
	 * mismo buzón. A un apoderado no se le escribe ahí salvo que otra persona haya aprobado ESE contacto.
	 */
	default boolean esContactoDelPersonal(String contacto) {
		return quienTieneElContacto(contacto).isPresent();
	}

	/** El usuario del personal activo cuyo celular o correo es (normalizado) ese contacto. */
	default Optional<Usuario> quienTieneElContacto(String contacto) {
		if (ContactoNormal.de(contacto).isEmpty()) {
			return Optional.empty();
		}
		return personalActivo().stream().filter(u -> ContactoNormal.iguales(u.getTelefonoWhatsapp(), contacto)
				|| ContactoNormal.iguales(u.getCorreo(), contacto)).findFirst();
	}
}
