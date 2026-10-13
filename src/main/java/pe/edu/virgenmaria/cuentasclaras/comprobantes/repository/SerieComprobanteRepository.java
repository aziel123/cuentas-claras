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

	/** Sprint 8, tanda 2: la serie (si ya existe en el colegio) para validar un conector OSE antes de su vigencia. */
	Optional<SerieComprobante> findBySerie(String serie);

	/** Bloquea la serie para tomar el número siguiente: dos cobros a la vez no toman el mismo número. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SerieComprobante s where s.serie = :serie")
	Optional<SerieComprobante> bloquear(@Param("serie") String serie);

	List<SerieComprobante> findAll();

	/**
	 * Correcciones del sprint 8 (QA-S8-6): cuáles de esas series ya existen en esos colegios. Solo con
	 * {@code ContextoColegio.comoSistema} (otras sedes del mismo RUC): {@code SeriesDeOtrasSedes}.
	 */
	@Query("select s.serie from SerieComprobante s where s.serie in :series and s.colegioId in :colegios")
	List<String> seriesEn(@Param("series") java.util.Collection<String> series,
			@Param("colegios") java.util.Collection<Long> colegios);
}
