package pe.edu.virgenmaria.cuentasclaras.alumnos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Matrículas del colegio actual (las filtra {@code @TenantId}). */
public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

	Optional<Matricula> findByAlumnoIdAndAnioEscolarId(Long alumnoId, Long anioEscolarId);

	@Query("select m from Matricula m join fetch m.anioEscolar join fetch m.seccion where m.alumno.id = :alumnoId "
			+ "order by m.anioEscolar.anio desc")
	List<Matricula> findByAlumnoIdOrderByAnioEscolarAnioDesc(@Param("alumnoId") Long alumnoId);

	List<Matricula> findByAnioEscolarIdAndEstado(Long anioEscolarId, EstadoMatricula estado);

	@Query("select m from Matricula m join fetch m.seccion where m.anioEscolar.id = :anioId and m.alumno.id in :alumnos")
	List<Matricula> delAnioParaAlumnos(@Param("anioId") Long anioId, @Param("alumnos") Collection<Long> alumnos);

	long countBySeccionId(Long seccionId);

	/** [id de sección, matrículas activas] de un año. */
	@Query("select m.seccion.id, count(m) from Matricula m where m.anioEscolar.id = :anioId and m.estado = ACTIVA "
			+ "group by m.seccion.id")
	List<Object[]> contarActivasPorSeccion(@Param("anioId") Long anioId);
}
