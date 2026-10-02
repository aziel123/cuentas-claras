package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Secciones del colegio actual (las filtra {@code @TenantId}). */
public interface SeccionRepository extends JpaRepository<Seccion, Long> {

	List<Seccion> findByAnioEscolarIdOrderByGradoAscNombreAsc(Long anioEscolarId);

	List<Seccion> findByAnioEscolarIdAndGrado(Long anioEscolarId, Grado grado);

	Optional<Seccion> findByAnioEscolarIdAndGradoAndNombre(Long anioEscolarId, Grado grado, String nombre);

	/** Secciones activas de los años indicados, con su año ya cargado. */
	@Query("select s from Seccion s join fetch s.anioEscolar a where a.id in :anios and s.activa = true "
			+ "order by a.anio desc, s.grado asc, s.nombre asc")
	List<Seccion> activasDe(@Param("anios") Collection<Long> anios);

	/** Todas las secciones (activas o no) con su año ya cargado: filtros de la lista de alumnos. */
	@Query("select s from Seccion s join fetch s.anioEscolar a order by a.anio desc, s.grado asc, s.nombre asc")
	List<Seccion> todasConAnio();

	long countByAnioEscolarId(Long anioEscolarId);
}
