package pe.edu.virgenmaria.cuentasclaras.alumnos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
