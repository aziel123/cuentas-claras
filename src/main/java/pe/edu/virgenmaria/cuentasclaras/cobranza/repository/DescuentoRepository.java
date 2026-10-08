package pe.edu.virgenmaria.cuentasclaras.cobranza.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Descuento;

import java.util.List;
import java.util.Optional;

/** Descuentos del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface DescuentoRepository extends Repository<Descuento, Long> {

	Descuento save(Descuento descuento);

	Descuento saveAndFlush(Descuento descuento);

	Optional<Descuento> findById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Descuento d where d.id = :id")
	Optional<Descuento> bloquear(@Param("id") Long id);

	/** Las cuotas pedidas (columna inmutable): para bloquearlas ANTES que el descuento. */
	@Query("select d.cuotas from Descuento d where d.id = :id")
	Optional<String> cuotasDe(@Param("id") Long id);

	List<Descuento> findAllByOrderByIdDesc(Pageable pagina);

	List<Descuento> findByAlumnoIdOrderByIdDesc(Long alumnoId);

	/** ¿Alguna cuota (",12,") ya tiene un descuento en ese estado? */
	boolean existsByEstadoAndCuotasContaining(pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoDescuento estado,
			String cuota);
}
