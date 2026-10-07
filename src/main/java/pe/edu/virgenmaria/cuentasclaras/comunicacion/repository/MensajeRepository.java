package pe.edu.virgenmaria.cuentasclaras.comunicacion.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.Mensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Mensajes del colegio actual ({@code @TenantId}). Sin borrados, sin {@code @Modifying} y sin SQL nativo: el estado lo
 * cambia la entidad con sus métodos (y en MySQL lo vigila trg_mensaje_envio).
 */
public interface MensajeRepository extends Repository<Mensaje, Long> {

	Mensaje saveAndFlush(Mensaje mensaje);

	Optional<Mensaje> findById(Long id);

	Optional<Mensaje> findByClave(String clave);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Mensaje m where m.id = :id")
	Optional<Mensaje> bloquear(@Param("id") Long id);

	/** Los PENDIENTE cuyo intento ya toca, el más antiguo primero. */
	@Query("select m.id from Mensaje m where m.estado = pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje.PENDIENTE "
			+ "and (m.proximoIntentoEn is null or m.proximoIntentoEn <= :ahora) order by m.id")
	List<Long> porEnviar(@Param("ahora") LocalDateTime ahora, Pageable pagina);

	Optional<Mensaje> findByProveedorAndProveedorMensajeId(ProveedorMensajeria proveedor, String proveedorMensajeId);

	/** Historial del portal: los mensajes a los apoderados de una familia desde una fecha. */
	List<Mensaje> findByFamiliaIdAndCreadoEnGreaterThanEqualOrderByIdDesc(Long familiaId, LocalDateTime desde);

	List<Mensaje> findByEntidadAndEntidadIdOrderByIdAsc(String entidad, Long entidadId);

	List<Mensaje> findByTipoAndEntidadAndEntidadIdOrderByIdAsc(TipoMensaje tipo, String entidad, Long entidadId);

	/** Bandeja del personal: los de un estado. */
	List<Mensaje> findTop100ByEstadoOrderByIdDesc(EstadoMensaje estado);

	/** Bandeja del personal: los de hoy. */
	List<Mensaje> findTop200ByCreadoEnGreaterThanEqualOrderByIdDesc(LocalDateTime desde);

	boolean existsByTipoAndEntidadAndEntidadIdAndCanalAndApoderadoId(TipoMensaje tipo, String entidad, Long entidadId,
			CanalMensaje canal, Long apoderadoId);

	long countByProveedor(ProveedorMensajeria proveedor);

	long countByEstadoAndCreadoEnLessThan(EstadoMensaje estado, LocalDateTime antes);

	/**
	 * Pagos vigentes registrados en el rango que no tienen ningún aviso que haya salido (ENVIADO, ENTREGADO o LEIDO):
	 * alerta CRÍTICA de Promotoría (G2 y G10).
	 */
	@Query("select p.id from Pago p where p.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE "
			+ "and p.creadoEn >= :desde and p.creadoEn < :hasta and not exists (select 1 from Mensaje m "
			+ "where m.tipo = pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje.PAGO_REGISTRADO "
			+ "and m.entidad = 'pago' and m.entidadId = p.id and m.estado in :salieron)")
	List<Long> pagosSinAvisoEnviado(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
			@Param("salieron") Collection<EstadoMensaje> salieron);

	/**
	 * Avisos financieros FALLIDOS desde una fecha cuyo pago, anulación o descuento no tiene ningún otro aviso que haya
	 * salido (falló en todos sus canales).
	 */
	@Query("select m from Mensaje m where m.estado = pe.edu.virgenmaria.cuentasclaras.comunicacion.model.EstadoMensaje.FALLIDO "
			+ "and m.tipo in :tipos and m.creadoEn >= :desde and not exists (select 1 from Mensaje o "
			+ "where o.tipo = m.tipo and o.entidad = m.entidad and o.entidadId = m.entidadId and o.estado in :salieron)")
	List<Mensaje> fallidosEnTodosSusCanales(@Param("tipos") Collection<TipoMensaje> tipos,
			@Param("desde") LocalDateTime desde, @Param("salieron") Collection<EstadoMensaje> salieron);
}
