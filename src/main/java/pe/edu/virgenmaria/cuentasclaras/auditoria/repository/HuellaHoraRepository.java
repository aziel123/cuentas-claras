package pe.edu.virgenmaria.cuentasclaras.auditoria.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.HuellaHora;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Huellas por hora del colegio actual ({@code @TenantId}). Solo inserción y lectura. */
public interface HuellaHoraRepository extends Repository<HuellaHora, Long> {

	HuellaHora saveAndFlush(HuellaHora huella);

	Optional<HuellaHora> findFirstByOrderBySecuenciaDesc();

	List<HuellaHora> findByMomentoGreaterThanEqualOrderByMomentoAsc(LocalDateTime desde);
}
