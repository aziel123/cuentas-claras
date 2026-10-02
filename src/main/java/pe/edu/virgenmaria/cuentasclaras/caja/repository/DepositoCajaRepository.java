package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Depósitos del colegio actual ({@code @TenantId}). Solo inserción: sin borrados ni {@code @Modifying}. */
public interface DepositoCajaRepository extends Repository<DepositoCaja, Long> {

	DepositoCaja save(DepositoCaja deposito);

	Optional<DepositoCaja> findById(Long id);

	Optional<DepositoCaja> findByCajaId(Long cajaId);

	boolean existsByCajaId(Long cajaId);

	List<DepositoCaja> findByCajaIdIn(Collection<Long> cajas);

	/** Depósitos que Administración aún no comparó con el banco. */
	@Query("select d from DepositoCaja d where not exists (select v.id from VerificacionBancaria v "
			+ "where v.deposito = d) order by d.fechaDeposito, d.id")
	List<DepositoCaja> sinVerificar();

	/** Los últimos depósitos por un monto distinto de lo contado (alerta). */
	@Query("select d from DepositoCaja d where d.monto <> d.esperado order by d.id desc")
	List<DepositoCaja> distintos();
}
