package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;

import java.util.Optional;

/**
 * Sprint 10, tanda 1: la configuración de evaluación de cada año del colegio actual (la filtra {@code @TenantId}). Sin
 * {@code delete*} ni {@code @Modifying}: cambia solo por sus métodos, con la firma de Dirección.
 */
public interface ConfiguracionEvaluacionRepository extends Repository<ConfiguracionEvaluacion, Long> {

	ConfiguracionEvaluacion saveAndFlush(ConfiguracionEvaluacion configuracion);

	Optional<ConfiguracionEvaluacion> findByAnioEscolarId(Long anioEscolarId);

	/** Lee y bloquea la del año: dos guardados a la vez se serializan (la versión sube de a una). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ConfiguracionEvaluacion c where c.anioEscolarId = :anio")
	Optional<ConfiguracionEvaluacion> bloquearDelAnio(@Param("anio") Long anioEscolarId);
}
