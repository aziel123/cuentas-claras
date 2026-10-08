package pe.edu.virgenmaria.cuentasclaras.alumnos.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Apoderados del colegio actual (los filtra {@code @TenantId}). */
public interface ApoderadoRepository extends JpaRepository<Apoderado, Long> {

	Optional<Apoderado> findByDocumentoTipoAndDocumentoNumero(TipoDocumento tipo, String numero);

	default Optional<Apoderado> findByDocumento(DocumentoIdentidad documento) {
		return findByDocumentoTipoAndDocumentoNumero(documento.tipo(), documento.numero());
	}

	List<Apoderado> findByDocumentoNumeroIn(Collection<String> numeros);

	List<Apoderado> findByFamiliaIdOrderByApellidoPaternoAsc(Long familiaId);

	/** Correcciones del sprint 5 (S5-A1 y S5-M5): los activos, para comparar sus contactos normalizados. */
	List<Apoderado> findByActivoTrueOrderByIdAsc();

	/** Sprint 6, tanda 3: los apoderados activos de varias familias (la llamada de control). */
	List<Apoderado> findByFamiliaIdInAndActivoTrueOrderByIdAsc(Collection<Long> familiaIds);

	/**
	 * Búsqueda por nombre (hasta tres palabras, sin tildes) o por el inicio del documento, como la de alumnos. Cada
	 * parámetro ya viene escapado y con sus comodines; si no se usa, va null (al menos uno debe venir).
	 */
	@Query("select a from Apoderado a where (:t1 is null or a.nombreBusqueda like :t1 escape '!') "
			+ "and (:t2 is null or a.nombreBusqueda like :t2 escape '!') "
			+ "and (:t3 is null or a.nombreBusqueda like :t3 escape '!') "
			+ "and (:documento is null or a.documento.numero like :documento escape '!') order by a.nombreBusqueda, a.id")
	List<Apoderado> buscar(@Param("t1") String t1, @Param("t2") String t2, @Param("t3") String t3,
			@Param("documento") String prefijoDocumento, Pageable pagina);
}
