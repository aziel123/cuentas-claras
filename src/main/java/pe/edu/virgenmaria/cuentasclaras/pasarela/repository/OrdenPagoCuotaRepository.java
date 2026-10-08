package pe.edu.virgenmaria.cuentasclaras.pasarela.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPagoCuota;

import java.util.Collection;
import java.util.List;

/** Cuotas de las órdenes (solo inserción). */
public interface OrdenPagoCuotaRepository extends Repository<OrdenPagoCuota, Long> {

	OrdenPagoCuota save(OrdenPagoCuota cuota);

	List<OrdenPagoCuota> findByOrdenIdOrderByIdAsc(Long ordenId);

	List<OrdenPagoCuota> findByOrdenIdInOrderByIdAsc(Collection<Long> ordenes);
}
