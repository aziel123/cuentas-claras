package pe.edu.virgenmaria.cuentasclaras.privacidad.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso;
import pe.edu.virgenmaria.cuentasclaras.privacidad.model.AccesoDatoPersonal;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Registro de accesos a datos personales (sprint 7, tanda 3): solo inserción y consultas, sin borrado ni JPQL de
 * escritura. Toda consulta la filtra Hibernate por colegio ({@code @TenantId}).
 */
public interface AccesoDatoPersonalRepository extends Repository<AccesoDatoPersonal, Long> {

	AccesoDatoPersonal saveAndFlush(AccesoDatoPersonal acceso);

	List<AccesoDatoPersonal> findTop200ByFamiliaIdAndCreadoEnGreaterThanEqualOrderByIdDesc(Long familiaId,
			LocalDateTime desde);

	List<AccesoDatoPersonal> findTop500ByCreadoEnGreaterThanEqualAndCreadoEnLessThanOrderByIdDesc(LocalDateTime desde,
			LocalDateTime hasta);

	List<AccesoDatoPersonal> findTop500ByUsuarioIdAndCreadoEnGreaterThanEqualAndCreadoEnLessThanOrderByIdDesc(
			Long usuarioId, LocalDateTime desde, LocalDateTime hasta);

	long countByCreadoEnGreaterThanEqual(LocalDateTime desde);

	/** Personas que vieron más de {@code umbral} fichas desde ese momento: [usuarioId, fichas]. */
	@Query("select a.usuarioId, count(a) from AccesoDatoPersonal a where a.creadoEn >= :desde and a.tipo in :tipos "
			+ "group by a.usuarioId having count(a) > :umbral order by count(a) desc")
	List<Object[]> personasConMasFichas(@Param("desde") LocalDateTime desde, @Param("tipos") Collection<TipoAcceso> tipos,
			@Param("umbral") long umbral);
}
