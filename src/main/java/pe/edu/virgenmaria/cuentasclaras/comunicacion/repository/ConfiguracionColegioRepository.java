package pe.edu.virgenmaria.cuentasclaras.comunicacion.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionColegio;

import java.util.Optional;

/** Solo lectura de {@code configuracion_colegio} (la escribe el DBA), siempre de UN colegio (QA-S6-6). */
public interface ConfiguracionColegioRepository extends Repository<ConfiguracionColegio, Long> {

	Optional<ConfiguracionColegio> findByColegioIdAndClave(Long colegioId, String clave);
}
