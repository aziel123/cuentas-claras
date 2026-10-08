package pe.edu.virgenmaria.cuentasclaras.pasarela.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoEvento;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EventoPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;

import java.util.List;
import java.util.Optional;

/** Bandeja de avisos de la pasarela del colegio actual. Sin borrados ni {@code @Modifying}. */
public interface EventoPasarelaRepository extends Repository<EventoPasarela, Long> {

	EventoPasarela save(EventoPasarela evento);

	EventoPasarela saveAndFlush(EventoPasarela evento);

	Optional<EventoPasarela> findById(Long id);

	Optional<EventoPasarela> findByProveedorAndEventoId(ProveedorPasarela proveedor, String eventoId);

	List<EventoPasarela> findByEstadoOrderByIdAsc(EstadoEvento estado);

	long countByEstado(EstadoEvento estado);
}
