package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Reembolso;

import java.util.Collection;
import java.util.List;

/** Reembolsos del colegio actual ({@code @TenantId}). Solo inserción: sin borrados ni {@code @Modifying}. */
public interface ReembolsoRepository extends Repository<Reembolso, Long> {

	Reembolso save(Reembolso reembolso);

	boolean existsByAnulacionId(Long anulacionId);

	List<Reembolso> findByAnulacionIdIn(Collection<Long> anulaciones);

	List<Reembolso> findTop30ByOrderByIdDesc();

	/** Reembolsos digitales de un rango de fechas (salen del banco como cargo: conciliación automática). */
	List<Reembolso> findByFechaBetweenAndMedioNotOrderByFechaAscIdAsc(java.time.LocalDate desde, java.time.LocalDate hasta,
			pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago medio);

	java.util.Optional<Reembolso> findById(Long id);
}
