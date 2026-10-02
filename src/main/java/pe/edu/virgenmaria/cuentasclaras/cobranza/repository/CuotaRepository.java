package pe.edu.virgenmaria.cuentasclaras.cobranza.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Cuotas del colegio actual. Sin borrados ni {@code @Modifying}: una cuota solo se crea y cambia de estado por sus
 * métodos (y en MySQL el UPDATE está limitado por columna).
 */
public interface CuotaRepository extends Repository<Cuota, Long> {

	Cuota save(Cuota cuota);

	Cuota saveAndFlush(Cuota cuota);

	Optional<Cuota> findById(Long id);

	List<Cuota> findByAlumnoIdOrderByFechaVencimientoAscIdAsc(Long alumnoId);

	boolean existsByClave(String clave);

	List<Cuota> findByAlumnoIdAndObligacionIn(Long alumnoId, Collection<String> obligaciones);

	boolean existsByMatriculaIdAndTipoIn(Long matriculaId, Collection<TipoCuota> tipos);

	boolean existsByMatriculaId(Long matriculaId);

	long countByAnioEscolarId(Long anioId);

	List<Cuota> findByLineaSaldoInicialIdIn(Collection<Long> lineas);

	/** Matrículas activas del año, de esos grados, que aún no tienen cuotas de matrícula ni de pensión. */
	@Query("select m from Matricula m join fetch m.alumno join fetch m.seccion s "
			+ "where m.anioEscolar.id = :anio and m.estado = pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula.ACTIVA "
			+ "and s.grado in :grados and not exists (select c.id from Cuota c where c.matriculaId = m.id "
			+ "and c.tipo in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota.MATRICULA, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota.PENSION)) order by m.id")
	List<Matricula> matriculasSinCronograma(@Param("anio") Long anioId, @Param("grados") Collection<Grado> grados);
}
