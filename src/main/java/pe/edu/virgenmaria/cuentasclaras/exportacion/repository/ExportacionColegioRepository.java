package pe.edu.virgenmaria.cuentasclaras.exportacion.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.EstadoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.ExportacionColegio;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Sprint 11, tanda 2: las exportaciones del colegio en contexto (filtradas por {@code @TenantId}). */
public interface ExportacionColegioRepository extends JpaRepository<ExportacionColegio, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from ExportacionColegio e where e.id = :id")
	Optional<ExportacionColegio> bloquear(@Param("id") Long id);

	Optional<ExportacionColegio> findByTokenHash(String tokenHash);

	List<ExportacionColegio> findAllByOrderByIdDesc();

	List<ExportacionColegio> findByEstadoInAndVenceEnLessThanEqual(Collection<EstadoExportacion> estados,
			LocalDateTime ahora);

	boolean existsByEstado(EstadoExportacion estado);
}
