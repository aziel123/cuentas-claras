package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EscalaLogro;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

import java.util.List;
import java.util.Optional;

/** Sprint 10, tanda 1: la escala de calificación del catálogo, solo lectura (sin save ni delete). */
public interface EscalaLogroRepository extends Repository<EscalaLogro, Long> {

	List<EscalaLogro> findByVersionIdOrderByNivelAscOrdenAsc(Long versionId);

	Optional<EscalaLogro> findByVersionIdAndNivelAndValor(Long versionId, Nivel nivel, String valor);
}
