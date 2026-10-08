package pe.edu.virgenmaria.cuentasclaras.caja.repository;

import org.springframework.data.repository.Repository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AnulacionPago;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Anulaciones aprobadas del colegio actual ({@code @TenantId}). SOLO INSERCIÓN. */
public interface AnulacionPagoRepository extends Repository<AnulacionPago, Long> {

	AnulacionPago save(AnulacionPago anulacion);

	Optional<AnulacionPago> findById(Long id);

	Optional<AnulacionPago> findByPagoId(Long pagoId);

	List<AnulacionPago> findByPagoIdIn(Collection<Long> pagoIds);

	/** Si la nota de crédito es de una anulación de ese tipo (la de una devolución lleva espacio para la firma). */
	boolean existsByNotaCreditoIdAndTipo(Long notaCreditoId, pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion tipo);

	/**
	 * Devoluciones aprobadas cuyo reembolso Administración aún no registra (alerta crítica): ni a mano (efectivo o
	 * transferencia) ni por la pasarela (S4-A3). Un contracargo no es una devolución: no espera reembolso.
	 */
	@org.springframework.data.jpa.repository.Query("select a from AnulacionPago a where "
			+ "a.tipo = pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAnulacion.DEVOLUCION and not exists "
			+ "(select r.id from Reembolso r where r.anulacion = a) and not exists "
			+ "(select x.id from ReembolsoPasarela x where x.anulacion = a) order by a.id")
	List<AnulacionPago> devolucionesSinReembolso();

	/** Anulaciones aprobadas desde un momento (alerta diaria de devoluciones en efectivo). */
	List<AnulacionPago> findByCreadoEnGreaterThanEqualOrderByIdAsc(java.time.LocalDateTime desde);
}
