package pe.edu.virgenmaria.cuentasclaras.cobranza.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
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

	List<Cuota> findByAlumnoIdAndAnioEscolarId(Long alumnoId, Long anioId);

	List<Cuota> findByAlumnoIdAndObligacionIn(Long alumnoId, Collection<String> obligaciones);

	boolean existsByMatriculaIdAndTipoIn(Long matriculaId, Collection<TipoCuota> tipos);

	boolean existsByMatriculaId(Long matriculaId);

	List<Cuota> findByMatriculaIdAndTipoOrderByFechaVencimientoAscIdAsc(Long matriculaId, TipoCuota tipo);

	long countByAnioEscolarId(Long anioId);

	/**
	 * Cuotas a cobrar, bloqueadas ({@code SELECT ... FOR UPDATE}) en orden de id: así dos cajeras no cobran la misma
	 * cuota y el orden de bloqueo es siempre el mismo. Sin {@code join fetch}: H2 no admite FOR UPDATE con uniones.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cuota c where c.id in :ids order by c.id")
	List<Cuota> bloquear(@Param("ids") Collection<Long> ids);

	/** Cuotas por pagar (PENDIENTE o PARCIAL) de todos los hermanos de una familia, de la más antigua a la más nueva. */
	@Query("select c from Cuota c join fetch c.alumno a where a.familia.id = :familia "
			+ "and c.estado in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PENDIENTE, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PARCIAL) order by c.fechaVencimiento, c.id")
	List<Cuota> porPagarDeFamilia(@Param("familia") Long familiaId);

	/** Cuotas por pagar (PENDIENTE o PARCIAL) de estos alumnos: para el resumen de deuda en la búsqueda de caja. */
	@Query("select c from Cuota c where c.alumno.id in :alumnos "
			+ "and c.estado in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PENDIENTE, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoCuota.PARCIAL)")
	List<Cuota> porPagarDeAlumnos(@Param("alumnos") Collection<Long> alumnoIds);

	List<Cuota> findByLineaSaldoInicialIdIn(Collection<Long> lineas);

	/** Matrículas activas del año, de esos grados, que aún no tienen cuotas de matrícula ni de pensión. */
	@Query("select m from Matricula m join fetch m.alumno join fetch m.seccion s "
			+ "where m.anioEscolar.id = :anio and m.estado = pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula.ACTIVA "
			+ "and s.grado in :grados and not exists (select c.id from Cuota c where c.matriculaId = m.id "
			+ "and c.tipo in (pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota.MATRICULA, "
			+ "pe.edu.virgenmaria.cuentasclaras.cobranza.model.TipoCuota.PENSION)) order by m.id")
	List<Matricula> matriculasSinCronograma(@Param("anio") Long anioId, @Param("grados") Collection<Grado> grados);

	/** Matrículas RETIRADAS del año, de esos grados, que no tienen ninguna cuota (un retiro que evitó todo el cobro). */
	@Query("select m from Matricula m join fetch m.alumno join fetch m.seccion s "
			+ "where m.anioEscolar.id = :anio and m.estado = pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula.RETIRADA "
			+ "and s.grado in :grados and not exists (select c.id from Cuota c where c.matriculaId = m.id) order by m.id")
	List<Matricula> retiradasSinCuotas(@Param("anio") Long anioId, @Param("grados") Collection<Grado> grados);
}
