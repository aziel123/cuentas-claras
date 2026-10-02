package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;

import java.util.List;
import java.util.Optional;

/** Pagos del colegio actual ({@code @TenantId}). Libro de solo inserción: sin borrados ni {@code @Modifying}. */
public interface PagoRepository extends Repository<Pago, Long> {

	Pago save(Pago pago);

	Optional<Pago> findById(Long id);

	Optional<Pago> findByClaveIdempotencia(String clave);

	/** Un número de operación digital vigente no se registra dos veces (también es UNIQUE en la base). */
	boolean existsByMedioAndOperacionVigente(MedioPago medio, String operacionVigente);

	List<Pago> findByCajaIdOrderByIdDesc(Long cajaId);

	List<Pago> findTop5ByFamiliaIdOrderByIdDesc(Long familiaId);
}
