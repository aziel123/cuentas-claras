package pe.edu.virgenmaria.cuentasclaras.comprobantes.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;

import java.util.List;
import java.util.Optional;

/** Series del colegio actual ({@code @TenantId}). Sin borrados: no extiende CrudRepository. */
public interface SerieComprobanteRepository extends Repository<SerieComprobante, Long> {

	SerieComprobante save(SerieComprobante serie);

	SerieComprobante saveAndFlush(SerieComprobante serie);

	boolean existsBySerie(String serie);

	/** Bloquea la serie para tomar el número siguiente: dos cobros a la vez no toman el mismo número. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SerieComprobante s where s.serie = :serie")
	Optional<SerieComprobante> bloquear(@Param("serie") String serie);

	List<SerieComprobante> findAll();
}
