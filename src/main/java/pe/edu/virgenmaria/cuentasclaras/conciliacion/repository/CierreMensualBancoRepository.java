package pe.edu.virgenmaria.cuentasclaras.conciliacion.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CierreMensualBanco;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoCierreMensual;

import java.util.List;
import java.util.Optional;

/** Cierres mensuales del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface CierreMensualBancoRepository extends Repository<CierreMensualBanco, Long> {

	CierreMensualBanco saveAndFlush(CierreMensualBanco cierre);

	Optional<CierreMensualBanco> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CierreMensualBanco c where c.id = :id")
	Optional<CierreMensualBanco> bloquear(@Param("id") Long id);

	boolean existsByCuentaIdAndAnioAndMes(Long cuentaId, int anio, int mes);

	Optional<CierreMensualBanco> findByCuentaIdAndAnioAndMes(Long cuentaId, int anio, int mes);

	List<CierreMensualBanco> findTop24ByOrderByAnioDescMesDescIdDesc();

	List<CierreMensualBanco> findByEstadoOrderByAnioAscMesAsc(EstadoCierreMensual estado);
}
