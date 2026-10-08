package pe.edu.virgenmaria.cuentasclaras.panel.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.panel.model.DelegacionLlamada;

import java.time.LocalDate;
import java.util.Optional;

/** Delegaciones de las llamadas de control a Dirección ({@code @TenantId}). Solo inserción y lectura. */
public interface DelegacionLlamadaRepository extends Repository<DelegacionLlamada, Long> {

	DelegacionLlamada saveAndFlush(DelegacionLlamada delegacion);

	Optional<DelegacionLlamada> findBySemana(LocalDate semana);
}
