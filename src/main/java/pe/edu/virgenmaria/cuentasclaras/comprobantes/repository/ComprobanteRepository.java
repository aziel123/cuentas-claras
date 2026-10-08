package pe.edu.virgenmaria.cuentasclaras.comprobantes.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Comprobantes del colegio actual ({@code @TenantId}). Sin borrados ni {@code @Modifying}. */
public interface ComprobanteRepository extends Repository<Comprobante, Long> {

	Comprobante save(Comprobante comprobante);

	Optional<Comprobante> findById(Long id);

	/** Cuántos comprobantes tiene la serie: debe ser igual a su último número (si no, hay un hueco). */
	long countBySerie(String serie);

	/** La nota de crédito que anula ese comprobante (como máximo una). */
	Optional<Comprobante> findByModificaId(Long comprobanteId);

	/** La reemisión de un comprobante RECHAZADO (como máximo una: UNIQUE). */
	Optional<Comprobante> findByReemplazaId(Long comprobanteId);

	boolean existsByReemplazaId(Long comprobanteId);

	/**
	 * Outbox del OSE: PENDIENTE cuyo reintento ya toca (o que nunca se intentó y se emitió hace más de un minuto: el
	 * envío después del commit no corrió) y ENVIADO cuya consulta ya toca. El más antiguo primero.
	 */
	@Query("select c.id from Comprobante c where (c.estadoEnvio = pe.edu.virgenmaria.cuentasclaras.comprobantes.model"
			+ ".EstadoEnvio.PENDIENTE and ((c.proximoIntentoEn is null and c.creadoEn <= :emitidoAntes) "
			+ "or c.proximoIntentoEn <= :ahora)) or (c.estadoEnvio = pe.edu.virgenmaria.cuentasclaras.comprobantes.model"
			+ ".EstadoEnvio.ENVIADO and c.proximoIntentoEn <= :ahora) order by c.id")
	List<Long> porEnviar(@Param("ahora") LocalDateTime ahora, @Param("emitidoAntes") LocalDateTime emitidoAntes,
			Pageable pagina);

	/** Los aceptados en ese rango (la reconsulta nocturna). */
	List<Comprobante> findByEstadoEnvioInAndAceptadoEnGreaterThanEqualAndAceptadoEnLessThanOrderByIdAsc(
			Collection<EstadoEnvio> estados, LocalDateTime desde, LocalDateTime hasta);

	/** Bandeja por estado, del más antiguo al más nuevo (el plazo legal corre desde la emisión). */
	List<Comprobante> findByEstadoEnvioInOrderByFechaEmisionAscIdAsc(Collection<EstadoEnvio> estados);

	List<Comprobante> findTop50ByEstadoEnvioInOrderByIdDesc(Collection<EstadoEnvio> estados);

	/** El primer comprobante de un proveedor (para saber desde cuándo emite el OSE real). */
	Optional<Comprobante> findFirstByProveedorOrderByIdAsc(ProveedorComprobantes proveedor);

	boolean existsByProveedorAndIdGreaterThan(ProveedorComprobantes proveedor, Long id);
}
