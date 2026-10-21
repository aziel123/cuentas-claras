package pe.edu.virgenmaria.cuentasclaras.academico.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.academico.model.AvisoAsistenciaCorte;

import java.util.Optional;

/**
 * Sprint 9, tanda 2 (A2): la marca de agua de los avisos de asistencia de UN colegio (la clave es el colegio). La escribe
 * solo {@code sistema.asistencia}. Sin {@code delete*}.
 */
public interface AvisoAsistenciaCorteRepository extends Repository<AvisoAsistenciaCorte, Long> {

	AvisoAsistenciaCorte saveAndFlush(AvisoAsistenciaCorte corte);

	/** La lee y la bloquea: dos pasadas del proceso a la vez se serializan. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from AvisoAsistenciaCorte c where c.colegioId = :colegio")
	Optional<AvisoAsistenciaCorte> bloquear(@Param("colegio") Long colegioId);

	Optional<AvisoAsistenciaCorte> findById(Long colegioId);
}
