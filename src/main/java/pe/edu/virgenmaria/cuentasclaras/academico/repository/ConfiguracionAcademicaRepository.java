package pe.edu.virgenmaria.cuentasclaras.academico.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;

import java.util.Optional;

/**
 * Sprint 9, tanda 2: la configuración académica de UN colegio (la clave es el colegio: se lee siempre con
 * {@code ContextoColegio.actual()}). Sin {@code delete*}.
 */
public interface ConfiguracionAcademicaRepository extends Repository<ConfiguracionAcademica, Long> {

	ConfiguracionAcademica saveAndFlush(ConfiguracionAcademica configuracion);

	Optional<ConfiguracionAcademica> findById(Long colegioId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ConfiguracionAcademica c where c.colegioId = :colegio")
	Optional<ConfiguracionAcademica> bloquear(@Param("colegio") Long colegioId);
}
