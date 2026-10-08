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
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.PlantillaMensaje;
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

	/** QA-S5-4: si ya existe el recordatorio de una familia, tipo y fecha de vencimiento (la clave termina en la fecha). */
	boolean existsByTipoAndFamiliaIdAndClaveEndingWith(TipoMensaje tipo, Long familiaId, String fin);

	/** S5-M4: los últimos mensajes de un tipo (la huella enviada). */
	List<Mensaje> findTop60ByTipoOrderByIdDesc(TipoMensaje tipo);

	/** S5-B3: si el mensaje de una entidad ya salió. */
	boolean existsByTipoAndEntidadAndEntidadIdAndEstadoIn(TipoMensaje tipo, String entidad, Long entidadId,
			Collection<EstadoMensaje> estados);

	/** S5-A1: los mensajes a un apoderado de un tipo (la verificación de su contacto). */
	List<Mensaje> findByTipoAndApoderadoIdOrderByIdAsc(TipoMensaje tipo, Long apoderadoId);

	long countByEstadoAndCreadoEnLessThan(EstadoMensaje estado, LocalDateTime antes);

	/**
	 * S5-B2: pagos vigentes registrados en el rango en los que a ALGÚN responsable de pago de los alumnos pagados no le
	 * salió (ENVIADO, ENTREGADO o LEIDO) su aviso: alerta CRÍTICA de Promotoría (G2 y G10). Antes bastaba un aviso a
	 * cualquiera; ahora cada responsable es auditor de sus hijos.
	 */
	@Query("select distinct p.id from Pago p, AplicacionPago a join a.cuota c join c.alumno al join al.responsablePago r "
			+ "where a.pago = p and a.tipo = pe.edu.virgenmaria.cuentasclaras.caja.model.TipoAplicacion.APLICACION "
			+ "and p.estado = pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago.VIGENTE "
			+ "and p.creadoEn >= :desde and p.creadoEn < :hasta and r.activo = true and not exists (select 1 from Mensaje m "
			+ "where m.tipo = pe.edu.virgenmaria.cuentasclaras.comunicacion.model.TipoMensaje.PAGO_REGISTRADO "
			+ "and m.entidad = 'pago' and m.entidadId = p.id and m.apoderadoId = r.id and m.estado in :salieron)")
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

	/** Sprint 6, tanda 2: avisos de esos tipos creados en un rango (los avisos financieros del día en el resumen). */
	long countByTipoInAndCreadoEnGreaterThanEqualAndCreadoEnLessThan(Collection<TipoMensaje> tipos, LocalDateTime desde,
			LocalDateTime hasta);

	/** Sprint 6, tanda 2: de esos, los que ya salieron (ENVIADO, ENTREGADO o LEIDO) al corte del resumen. */
	long countByTipoInAndCreadoEnGreaterThanEqualAndCreadoEnLessThanAndEstadoIn(Collection<TipoMensaje> tipos,
			LocalDateTime desde, LocalDateTime hasta, Collection<EstadoMensaje> estados);

	/** Sprint 6, tanda 2 (tope diario): mensajes de una plantilla a una persona desde un momento, sin los respaldos. */
	long countByTipoAndPlantillaAndUsuarioIdAndRespaldoDeIdIsNullAndCreadoEnGreaterThanEqual(TipoMensaje tipo,
			PlantillaMensaje plantilla, Long usuarioId, LocalDateTime desde);

	/** S6-B2: lo mismo, solo las claves que empiezan así (las alertas ATENCIÓN de un tipo: el tope no cuenta las CRÍTICAS). */
	long countByTipoAndPlantillaAndUsuarioIdAndRespaldoDeIdIsNullAndCreadoEnGreaterThanEqualAndClaveStartingWith(
			TipoMensaje tipo, PlantillaMensaje plantilla, Long usuarioId, LocalDateTime desde, String prefijo);

	/**
	 * Sprint 6 (lista de familias morosas): el último aviso ENTREGADO de esos tipos a cada familia, como familia, tipo y
	 * momento de entrega. Solo lectura.
	 */
	@Query("select m.familiaId, m.tipo, max(m.entregadoEn) from Mensaje m where m.familiaId in :familias "
			+ "and m.tipo in :tipos and m.entregadoEn is not null group by m.familiaId, m.tipo")
	List<Object[]> ultimosEntregados(@Param("familias") Collection<Long> familias,
			@Param("tipos") Collection<TipoMensaje> tipos);
}
