package pe.edu.virgenmaria.cuentasclaras.familias.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.familias.model.AvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.model.EstadoAvisoFamilia;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Avisos de las familias del colegio actual (los filtra {@code @TenantId}). Sin borrados ni ediciones por consulta. */
public interface AvisoFamiliaRepository extends Repository<AvisoFamilia, Long> {

	AvisoFamilia save(AvisoFamilia aviso);

	AvisoFamilia saveAndFlush(AvisoFamilia aviso);

	Optional<AvisoFamilia> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AvisoFamilia a where a.id = :id")
	Optional<AvisoFamilia> bloquear(@Param("id") Long id);

	List<AvisoFamilia> findByFamiliaIdOrderByIdDesc(Long familiaId);

	long countByFamiliaIdAndCreadoEnGreaterThanEqual(Long familiaId, LocalDateTime desde);

	List<AvisoFamilia> findByEstadoOrderByIdAsc(EstadoAvisoFamilia estado);

	List<AvisoFamilia> findTop50ByEstadoOrderByIdDesc(EstadoAvisoFamilia estado);
}
