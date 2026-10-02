package pe.edu.virgenmaria.cuentasclaras.alumnos.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Alumnos del colegio actual (los filtra {@code @TenantId}, también en la búsqueda JPQL). */
public interface AlumnoRepository extends JpaRepository<Alumno, Long> {

	String FILTROS_BUSQUEDA = """
			from Alumno a
			where (:t1 is null or a.nombreBusqueda like :t1 escape '!')
			  and (:t2 is null or a.nombreBusqueda like :t2 escape '!')
			  and (:t3 is null or a.nombreBusqueda like :t3 escape '!')
			  and (:documento is null or a.documento.numero like :documento escape '!')
			  and (:estado is null or a.estado = :estado)
			  and (:anioId is null or exists (select 1 from Matricula m
			       where m.alumno = a and m.anioEscolar.id = :anioId and m.estado = ACTIVA))
			  and (:seccionId is null or exists (select 1 from Matricula m
			       where m.alumno = a and m.seccion.id = :seccionId and m.estado = ACTIVA))
			""";

	Optional<Alumno> findByDocumentoTipoAndDocumentoNumero(TipoDocumento tipo, String numero);

	List<Alumno> findByDocumentoNumeroIn(Collection<String> numeros);

	List<Alumno> findByFamiliaIdOrderByFechaNacimientoAsc(Long familiaId);

	List<Alumno> findByResponsablePagoIdOrderByFechaNacimientoAsc(Long apoderadoId);

	boolean existsByResponsablePagoIdAndEstado(Long apoderadoId, EstadoAlumno estado);

	/**
	 * Búsqueda por nombre (hasta tres palabras, en cualquier orden, sin tildes) o por el inicio del documento.
	 * Cada parámetro de texto ya viene escapado y con sus comodines ({@code %PALABRA%}); si no se usa, va null.
	 */
	@Query(value = "select a " + FILTROS_BUSQUEDA + " order by a.nombreBusqueda, a.id",
			countQuery = "select count(a) " + FILTROS_BUSQUEDA)
	Page<Alumno> buscar(@Param("t1") String t1, @Param("t2") String t2, @Param("t3") String t3,
			@Param("documento") String prefijoDocumento, @Param("estado") EstadoAlumno estado,
			@Param("anioId") Long anioId, @Param("seccionId") Long seccionId, Pageable pagina);
}
