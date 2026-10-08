package pe.edu.virgenmaria.cuentasclaras.panel.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResumenDiario;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Fotos del resumen diario del colegio actual ({@code @TenantId}). SOLO inserción y lectura: sin borrados, sin
 * {@code @Modifying} y sin SQL nativo (reglas ArchUnit).
 */
public interface ResumenDiarioRepository extends Repository<ResumenDiario, Long> {

	ResumenDiario saveAndFlush(ResumenDiario resumen);

	Optional<ResumenDiario> findById(Long id);

	Optional<ResumenDiario> findByFecha(LocalDate fecha);

	/** Las fotos desde una fecha (el recálculo de los últimos días), de la más antigua a la más reciente. */
	List<ResumenDiario> findByFechaGreaterThanEqualOrderByFechaAsc(LocalDate desde);

	boolean existsByFechaGreaterThanEqual(LocalDate desde);

	/** Correcciones del sprint 6 (S6-B1, QA-S6-5): las fotos hasta un día, de la más reciente a la más antigua. */
	List<ResumenDiario> findTop400ByFechaLessThanEqualOrderByFechaDesc(LocalDate hasta);

	/** La foto anterior a un día (S6-M3: los cierres con diferencia «desde el resumen anterior»). */
	Optional<ResumenDiario> findFirstByFechaLessThanOrderByFechaDesc(LocalDate fecha);
}
