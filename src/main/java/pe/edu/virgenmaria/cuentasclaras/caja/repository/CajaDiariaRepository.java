package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCaja;

import java.time.LocalDate;
import java.util.Optional;

/** Cajas del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface CajaDiariaRepository extends Repository<CajaDiaria, Long> {

	CajaDiaria save(CajaDiaria caja);

	CajaDiaria saveAndFlush(CajaDiaria caja);

	Optional<CajaDiaria> findById(Long id);

	Optional<CajaDiaria> findByCajeroAndFecha(String cajero, LocalDate fecha);

	/**
	 * Bloquea la caja del cajero en ese día: es lo PRIMERO que hace un cobro. Serializa los cobros de una misma cajera
	 * (el doble clic espera y encuentra el pago ya registrado con su clave).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CajaDiaria c where c.cajero = :cajero and c.fecha = :fecha")
	Optional<CajaDiaria> bloquear(@Param("cajero") String cajero, @Param("fecha") LocalDate fecha);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CajaDiaria c where c.id = :id")
	Optional<CajaDiaria> bloquearPorId(@Param("id") Long id);

	/** La caja más antigua del cajero que sigue abierta antes de esa fecha: no se cobra sin cerrarla. */
	Optional<CajaDiaria> findFirstByCajeroAndEstadoAndFechaBeforeOrderByFechaAsc(String cajero, EstadoCaja estado,
			LocalDate fecha);
}
