package pe.edu.virgenmaria.cuentasclaras.auditoria.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Function;

/**
 * Evento de la bitácora de auditoría. SOLO INSERCIÓN:
 * <ul>
 *   <li>{@code @Immutable}: Hibernate ignora cambios a un evento cargado y rechaza el UPDATE en JPQL;</li>
 *   <li>{@code @PreUpdate}/{@code @PreRemove} lanzan excepción ({@code em.remove()} sí borraría la fila);</li>
 *   <li>el repositorio no tiene métodos de borrado ni edición y en MySQL la app no tiene UPDATE ni DELETE;</li>
 *   <li>el {@code hash} encadena el evento con el anterior (HMAC-SHA256), así un cambio directo en la base se detecta.</li>
 * </ul>
 * No extiende {@code BaseEntity}: no es una entidad del colegio (se registra también sin colegio y la cadena es
 * global). Por eso toda consulta debe filtrar por {@code colegioId} de forma explícita.
 */
@Entity
@Immutable
@Table(name = "evento_auditoria")
public class EventoAuditoria {

	public static final int MAX_NOMBRE_USUARIO = 60;
	public static final int MAX_ROLES = 120;
	public static final int MAX_ENTIDAD = 40;
	public static final int MAX_ENTIDAD_ID = 40;
	public static final int MAX_VALOR = 2000;
	public static final int MAX_DETALLE = 500;
	public static final int MAX_IP = 45;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private long secuencia;

	@Column(name = "colegio_id", updatable = false)
	private Long colegioId;

	@Column(name = "ocurrido_en", nullable = false, updatable = false)
	private LocalDateTime ocurridoEn;

	@Column(name = "usuario_id", updatable = false)
	private Long usuarioId;

	@Column(name = "nombre_usuario", nullable = false, updatable = false, length = MAX_NOMBRE_USUARIO)
	private String nombreUsuario;

	@Column(updatable = false, length = MAX_ROLES)
	private String roles;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 40)
	private AccionAuditoria accion;

	@Column(updatable = false, length = MAX_ENTIDAD)
	private String entidad;

	@Column(name = "entidad_id", updatable = false, length = MAX_ENTIDAD_ID)
	private String entidadId;

	@Column(name = "valor_anterior", updatable = false, length = MAX_VALOR)
	private String valorAnterior;

	@Column(name = "valor_nuevo", updatable = false, length = MAX_VALOR)
	private String valorNuevo;

	@Column(updatable = false, length = MAX_DETALLE)
	private String detalle;

	@Column(updatable = false, length = MAX_IP)
	private String ip;

	@Column(nullable = false, updatable = false, length = 64)
	private String hash;

	protected EventoAuditoria() {
		// requerido por JPA
	}

	/**
	 * Crea el evento con los textos recortados a lo que admite la tabla y lo sella: el hash se calcula
	 * sobre los valores ya recortados, que son los que se guardan.
	 *
	 * @param sellador calcula el hash del evento (encadenado con el anterior)
	 */
	public static EventoAuditoria crear(long secuencia, Actor actor, AccionAuditoria accion, String entidad,
			String entidadId, String valorAnterior, String valorNuevo, String detalle, LocalDateTime ocurridoEn,
			Function<EventoAuditoria, String> sellador) {
		Objects.requireNonNull(actor, "actor");
		EventoAuditoria evento = new EventoAuditoria();
		evento.secuencia = secuencia;
		evento.colegioId = actor.colegioId();
		evento.ocurridoEn = Objects.requireNonNull(ocurridoEn, "ocurridoEn");
		evento.usuarioId = actor.usuarioId();
		evento.nombreUsuario = recortar(actor.nombreUsuario(), MAX_NOMBRE_USUARIO);
		evento.roles = recortar(actor.roles(), MAX_ROLES);
		evento.accion = Objects.requireNonNull(accion, "accion");
		evento.entidad = recortar(entidad, MAX_ENTIDAD);
		evento.entidadId = recortar(entidadId, MAX_ENTIDAD_ID);
		evento.valorAnterior = recortar(valorAnterior, MAX_VALOR);
		evento.valorNuevo = recortar(valorNuevo, MAX_VALOR);
		evento.detalle = recortar(detalle, MAX_DETALLE);
		evento.ip = recortar(actor.ip(), MAX_IP);
		evento.hash = Objects.requireNonNull(sellador.apply(evento), "hash");
		return evento;
	}

	private static String recortar(String texto, int maximo) {
		if (texto == null || texto.length() <= maximo) {
			return texto;
		}
		return texto.substring(0, maximo);
	}

	@PreUpdate
	void impedirEdicion() {
		throw new UnsupportedOperationException("La auditoría es de solo inserción: un evento no se edita");
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("La auditoría es de solo inserción: un evento no se borra");
	}

	public Long getId() {
		return id;
	}

	public long getSecuencia() {
		return secuencia;
	}

	public Long getColegioId() {
		return colegioId;
	}

	public LocalDateTime getOcurridoEn() {
		return ocurridoEn;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getNombreUsuario() {
		return nombreUsuario;
	}

	public String getRoles() {
		return roles;
	}

	public AccionAuditoria getAccion() {
		return accion;
	}

	public String getEntidad() {
		return entidad;
	}

	public String getEntidadId() {
		return entidadId;
	}

	public String getValorAnterior() {
		return valorAnterior;
	}

	public String getValorNuevo() {
		return valorNuevo;
	}

	public String getDetalle() {
		return detalle;
	}

	public String getIp() {
		return ip;
	}

	public String getHash() {
		return hash;
	}
}
