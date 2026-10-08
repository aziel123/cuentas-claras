package pe.edu.virgenmaria.cuentasclaras.conciliacion.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.BancoCuenta;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CuentaBancaria;

import java.util.List;
import java.util.Optional;

/** Cuentas del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}: una cuenta se desactiva. */
public interface CuentaBancariaRepository extends Repository<CuentaBancaria, Long> {

	CuentaBancaria save(CuentaBancaria cuenta);

	Optional<CuentaBancaria> findById(Long id);

	/** La cuenta bloqueada (primer bloqueo de la carga y de la confirmación de sus extractos: la cadena es una sola). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CuentaBancaria c where c.id = :id")
	Optional<CuentaBancaria> bloquear(@Param("id") Long id);

	List<CuentaBancaria> findByActivaTrueOrderByIdAsc();

	List<CuentaBancaria> findAllByOrderByActivaDescIdAsc();

	boolean existsByBancoAndNumero(BancoCuenta banco, String numero);
}
