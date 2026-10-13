package pe.edu.virgenmaria.cuentasclaras.comunicacion.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;

import java.util.List;
import java.util.Optional;

/**
 * {@code configuracion_colegio}, siempre de UN colegio (QA-S6-6). Sprint 8, tanda 1: la escribe el operador de la
 * plataforma (con cc_sistema y su firma); nunca se borra.
 */
public interface ConfiguracionColegioRepository extends Repository<ConfiguracionColegio, Long> {

	Optional<ConfiguracionColegio> findByColegioIdAndClave(Long colegioId, String clave);

	List<ConfiguracionColegio> findByColegioIdOrderByClaveAsc(Long colegioId);

	ConfiguracionColegio saveAndFlush(ConfiguracionColegio configuracion);
}
