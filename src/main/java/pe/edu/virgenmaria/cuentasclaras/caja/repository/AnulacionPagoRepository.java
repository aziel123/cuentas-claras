package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AnulacionPago;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Anulaciones aprobadas del colegio actual ({@code @TenantId}). SOLO INSERCIÓN. */
public interface AnulacionPagoRepository extends Repository<AnulacionPago, Long> {

	AnulacionPago save(AnulacionPago anulacion);

	Optional<AnulacionPago> findByPagoId(Long pagoId);

	List<AnulacionPago> findByPagoIdIn(Collection<Long> pagoIds);

	/** Devoluciones aprobadas después del cierre de su caja: el reembolso lo hace Administración desde el banco. */
	List<AnulacionPago> findByPosteriorAlCierreTrueAndTipoOrderByIdDesc(
			pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion tipo);
}
