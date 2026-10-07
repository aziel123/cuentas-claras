package pe.edu.virgenmaria.cuentasclaras.auditoria.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.HuellaGuardada;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Huellas diarias del colegio actual ({@code @TenantId}). Solo inserción y lectura. */
public interface HuellaGuardadaRepository extends Repository<HuellaGuardada, Long> {

	HuellaGuardada saveAndFlush(HuellaGuardada huella);

	Optional<HuellaGuardada> findByFecha(LocalDate fecha);

	List<HuellaGuardada> findByFechaGreaterThanEqualOrderByFechaAsc(LocalDate desde);
}
