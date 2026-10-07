package pe.edu.virgenmaria.cuentasclaras.comunicacion.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ConfiguracionBd;

import java.util.Optional;

/** Solo lectura de {@code configuracion_bd} (la escribe el DBA). */
public interface ConfiguracionBdRepository extends Repository<ConfiguracionBd, String> {

	Optional<ConfiguracionBd> findById(String clave);
}
