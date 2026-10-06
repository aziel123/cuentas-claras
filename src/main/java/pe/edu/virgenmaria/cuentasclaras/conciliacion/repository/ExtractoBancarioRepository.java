package pe.edu.virgenmaria.cuentasclaras.conciliacion.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ExtractoBancario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Extractos del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface ExtractoBancarioRepository extends Repository<ExtractoBancario, Long> {

	ExtractoBancario save(ExtractoBancario extracto);

	ExtractoBancario saveAndFlush(ExtractoBancario extracto);

	Optional<ExtractoBancario> findById(Long id);

	/** El último extracto vigente (CARGADO o CONFIRMADO) de la cuenta: el siguiente debe continuarlo. */
	Optional<ExtractoBancario> findFirstByCuentaIdAndSecuenciaVigenteIsNotNullOrderBySecuenciaDesc(Long cuentaId);

	/** La cadena vigente de la cuenta, en orden. */
	List<ExtractoBancario> findByCuentaIdAndSecuenciaVigenteIsNotNullOrderBySecuenciaAsc(Long cuentaId);

	List<ExtractoBancario> findByCuentaIdAndEstadoOrderBySecuenciaAsc(Long cuentaId, EstadoExtracto estado);

	List<ExtractoBancario> findByEstadoOrderByIdAsc(EstadoExtracto estado);

	List<ExtractoBancario> findByEstadoOrderByHastaDesc(EstadoExtracto estado);

	List<ExtractoBancario> findTop50ByOrderByIdDesc();

	List<ExtractoBancario> findByEstadoAndRechazadoEnAfterOrderByIdDesc(EstadoExtracto estado, LocalDateTime desde);

	/** Si ya tiene uno siguiente vigente (entonces no se descarta ni se rechaza). */
	boolean existsByAnteriorIdAndSecuenciaVigenteIsNotNull(Long anteriorId);

	/** S4-A1: si la cuenta tiene un extracto en ese estado cuyos días se superponen con {@code desde}..{@code hasta}. */
	boolean existsByCuentaIdAndEstadoAndDesdeLessThanEqualAndHastaGreaterThanEqual(Long cuentaId, EstadoExtracto estado,
			LocalDate hasta, LocalDate desde);
}
