package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.CurriculoVersion;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 10, tanda 1: el catálogo nacional es de SOLO LECTURA para la aplicación (sin {@code save} ni {@code delete*}: no
 * extiende CrudRepository; lo exige {@code ReglasArquitecturaTest.nadieEscribeElCatalogo}). Las filas las inserta la
 * migración con cc_migrador.
 */
public interface CurriculoVersionRepository extends Repository<CurriculoVersion, Long> {

	Optional<CurriculoVersion> findById(Long id);

	Optional<CurriculoVersion> findByCodigo(String codigo);

	List<CurriculoVersion> findAllByOrderByCodigoAsc();
}
