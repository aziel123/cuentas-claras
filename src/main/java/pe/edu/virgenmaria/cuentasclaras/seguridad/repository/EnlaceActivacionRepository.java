package pe.edu.virgenmaria.cuentasclaras.seguridad.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.EnlaceActivacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Enlaces de activación del colegio actual ({@code @TenantId}). Sin borrados. */
public interface EnlaceActivacionRepository extends Repository<EnlaceActivacion, Long> {

	EnlaceActivacion save(EnlaceActivacion enlace);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from EnlaceActivacion e where e.hashToken = :hash")
	Optional<EnlaceActivacion> bloquearPorHash(@Param("hash") String hashToken);

	List<EnlaceActivacion> findByUsuarioIdOrderByIdDesc(Long usuarioId);

	/** Los usados desde una fecha (alerta: activados desde la IP de quien los creó). */
	List<EnlaceActivacion> findByUsadoEnAfterOrderByIdDesc(LocalDateTime desde);
}
