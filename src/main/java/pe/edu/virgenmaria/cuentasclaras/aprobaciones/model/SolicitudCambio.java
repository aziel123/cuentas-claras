package pe.edu.virgenmaria.cuentasclaras.aprobaciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Un cambio pedido por una persona que aprueba o rechaza otra. Lo que se pidió (tipo, entidad, datos, motivo y quién
 * lo pidió) no cambia nunca: solo se resuelve (GRANT por columna en MySQL). Una sola pendiente por tipo y entidad.
 */
@Entity
@Table(name = "solicitud_cambio")
public class SolicitudCambio extends BaseEntity {

	public static final int MAX_RESUMEN = 300;

	public static final int MAX_DATOS = 2000;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 40)
	private TipoSolicitud tipo;

	@Column(nullable = false, updatable = false, length = 40)
	private String entidad;

	@Column(name = "entidad_id", nullable = false, updatable = false)
	private Long entidadId;

	@Column(nullable = false, updatable = false, length = MAX_RESUMEN)
	private String resumen;

	/** JSON pequeño con lo necesario para aplicar el cambio (ver {@code DatosSolicitud}). */
	@Column(nullable = false, updatable = false, length = MAX_DATOS)
	private String datos;

	@Column(nullable = false, updatable = false, length = 500)
	private String motivo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoSolicitud estado;

	/** TRUE mientras está pendiente, NULL después: el UNIQUE impide dos pendientes del mismo tipo y entidad. */
	@Column
	private Boolean pendiente;

	@Column(name = "solicitado_por", nullable = false, updatable = false, length = 60)
	private String solicitadoPor;

	@Column(name = "resuelto_por", length = 60)
	private String resueltoPor;

	@Column(name = "resuelto_en")
	private LocalDateTime resueltoEn;

	@Column(length = 500)
	private String comentario;

	/**
	 * Sprint 11, tanda 1c (sección 3.16): TRUE si la pidió y la resolvió la misma Promotoría (aplicación directa); si no,
	 * NULL. En MySQL lo valida trg_solicitud_cambio_resuelta (rol, firma, código si es sensible, correo de control).
	 */
	@Column
	private Boolean directa;

	/** Tanda 1c (A1c-5): el CODIGO_PROMOTORIA de una directa sensible, o el CODIGO_CONTACTO de una cuenta directiva. */
	@Column(name = "codigo_mensaje_id")
	private Long codigoMensajeId;

	/** Tanda 1c (A1c-5): la SHA-256 de lo que escribió la persona (la base la compara con la del mensaje). */
	@Column(name = "codigo_escrito_sha256", length = 64)
	private String codigoEscritoSha256;

	/** Tanda 1c (A1c-6): fin de la espera de 24 horas. La pone SOLO la base (en H2 queda vacía). */
	@Column(name = "aplica_desde", insertable = false, updatable = false)
	private LocalDateTime aplicaDesde;

	@Column(name = "frenado_por", length = 60)
	private String frenadoPor;

	/** La hora del freno: la pone la base (en H2, nadie). */
	@Column(name = "frenado_en", insertable = false, updatable = false)
	private LocalDateTime frenadoEn;

	@Column(name = "motivo_freno", length = 500)
	private String motivoFreno;

	protected SolicitudCambio() {
		// requerido por JPA
	}

	public static SolicitudCambio nueva(TipoSolicitud tipo, String entidad, Long entidadId, String resumen, String datos,
			String motivo, String solicitante) {
		SolicitudCambio solicitud = new SolicitudCambio();
		solicitud.tipo = Objects.requireNonNull(tipo, "tipo");
		solicitud.entidad = Objects.requireNonNull(entidad, "entidad");
		solicitud.entidadId = Objects.requireNonNull(entidadId, "entidadId");
		solicitud.resumen = recortar(Objects.requireNonNull(resumen, "resumen"), MAX_RESUMEN);
		if (datos == null || datos.length() > MAX_DATOS) {
			throw new IllegalArgumentException("Datos de la solicitud vacíos o demasiado largos");
		}
		solicitud.datos = datos;
		solicitud.motivo = Motivo.exigir(motivo);
		solicitud.estado = EstadoSolicitud.PENDIENTE;
		solicitud.pendiente = Boolean.TRUE;
		solicitud.solicitadoPor = Objects.requireNonNull(solicitante, "solicitante");
		return solicitud;
	}

	public void aprobar(String por, String comentarioAprobacion, LocalDateTime ahora) {
		resolver(EstadoSolicitud.APROBADA, por, comentarioAprobacion == null || comentarioAprobacion.isBlank() ? null
				: Motivo.exigir(comentarioAprobacion), ahora);
	}

	/**
	 * Sprint 11, tanda 1c (sección 3.16): la Promotoría que la pidió la aprueba ella misma (aplicación directa), con el
	 * código de esta acción si es sensible ({@code codigoMensajeId} y la SHA-256 de lo escrito; si no, {@code null}).
	 */
	public void aprobarDirecta(String por, Long codigoMensajeId, String codigoEscritoSha256, LocalDateTime ahora) {
		if (estado != EstadoSolicitud.PENDIENTE) {
			throw new ReglaNegocioException("La solicitud ya fue " + estado.etiqueta().toLowerCase() + ".");
		}
		if (!Objects.equals(por, solicitadoPor)) {
			throw new IllegalStateException("La directa la resuelve quien la pidió");
		}
		if ((codigoMensajeId == null) != (codigoEscritoSha256 == null)) {
			throw new IllegalArgumentException("El código va con su mensaje y lo escrito");
		}
		estado = EstadoSolicitud.APROBADA;
		pendiente = null;
		resueltoPor = por;
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
		comentario = null;
		directa = Boolean.TRUE;
		this.codigoMensajeId = codigoMensajeId;
		this.codigoEscritoSha256 = codigoEscritoSha256;
	}

	/**
	 * Sprint 11, tanda 1c (A1c-1): la persona dueña de una cuenta directiva confirma el cambio de su contacto con el código
	 * que le llegó a su contacto ANTERIOR (una vez, mientras está pendiente).
	 */
	public void confirmarDesdeContactoAnterior(Long codigoMensajeId, String codigoEscritoSha256) {
		if (estado != EstadoSolicitud.PENDIENTE) {
			throw new ReglaNegocioException("La solicitud ya fue " + estado.etiqueta().toLowerCase() + ".");
		}
		if (this.codigoMensajeId != null) {
			throw new ReglaNegocioException("Este cambio ya está confirmado desde el contacto anterior.");
		}
		this.codigoMensajeId = Objects.requireNonNull(codigoMensajeId, "codigoMensajeId");
		this.codigoEscritoSha256 = Objects.requireNonNull(codigoEscritoSha256, "codigoEscritoSha256");
	}

	/** Si la persona afectada ya confirmó el cambio de su contacto desde el contacto anterior (A1c-1). */
	public boolean confirmadaDesdeContactoAnterior() {
		return codigoMensajeId != null && !isDirecta();
	}

	/**
	 * Sprint 11, tanda 1c (A1c-2, A1c-3): frena una directa que espera (una vez, con motivo de 20 caracteres o más).
	 * {@code venceEspera}: cuándo termina la espera (la de la base o la que calcula la aplicación en H2).
	 */
	public void frenar(String por, String motivo, LocalDateTime ahora, LocalDateTime venceEspera) {
		if (estado != EstadoSolicitud.APROBADA || !Boolean.TRUE.equals(directa) || venceEspera == null) {
			throw new ReglaNegocioException("Esta solicitud no está en espera: no se frena.");
		}
		if (frenadoPor != null) {
			throw new ReglaNegocioException("Esta solicitud ya está frenada por " + frenadoPor + ".");
		}
		if (!ahora.isBefore(venceEspera)) {
			throw new ReglaNegocioException("La espera ya terminó: el cambio se aplica (o ya se aplicó).");
		}
		String texto = motivo == null ? "" : motivo.strip();
		if (texto.length() < 20 || texto.length() > 500) {
			throw new ReglaNegocioException("Escribe por qué la frenas (de 20 a 500 caracteres): lo leen todas las "
					+ "Promotorías y el correo de control.");
		}
		frenadoPor = Objects.requireNonNull(por, "por");
		motivoFreno = texto;
	}

	public void rechazar(String por, String motivoRechazo, LocalDateTime ahora) {
		resolver(EstadoSolicitud.RECHAZADA, por, Motivo.exigir(motivoRechazo), ahora);
	}

	private void resolver(EstadoSolicitud nuevo, String por, String texto, LocalDateTime ahora) {
		if (estado != EstadoSolicitud.PENDIENTE) {
			throw new ReglaNegocioException("La solicitud ya fue " + estado.etiqueta().toLowerCase() + ".");
		}
		if (Objects.equals(por, solicitadoPor)) {
			throw new IllegalStateException("Quien solicita no resuelve (lo valida el servicio y la base)");
		}
		estado = nuevo;
		pendiente = null;
		resueltoPor = por;
		resueltoEn = Objects.requireNonNull(ahora, "ahora");
		comentario = texto;
	}

	public boolean estaPendiente() {
		return estado == EstadoSolicitud.PENDIENTE;
	}

	private static String recortar(String texto, int maximo) {
		return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las solicitudes no se borran: se aprueban o se rechazan.");
	}

	public TipoSolicitud getTipo() {
		return tipo;
	}

	public String getEntidad() {
		return entidad;
	}

	public Long getEntidadId() {
		return entidadId;
	}

	public String getResumen() {
		return resumen;
	}

	public String getDatos() {
		return datos;
	}

	public String getMotivo() {
		return motivo;
	}

	public EstadoSolicitud getEstado() {
		return estado;
	}

	public String getSolicitadoPor() {
		return solicitadoPor;
	}

	public String getResueltoPor() {
		return resueltoPor;
	}

	public LocalDateTime getResueltoEn() {
		return resueltoEn;
	}

	public String getComentario() {
		return comentario;
	}

	public boolean isDirecta() {
		return Boolean.TRUE.equals(directa);
	}

	public Long getCodigoMensajeId() {
		return codigoMensajeId;
	}

	public String getCodigoEscritoSha256() {
		return codigoEscritoSha256;
	}

	/** El fin de la espera que puso la base (MySQL), o {@code null} (H2 o una solicitud sin espera). */
	public LocalDateTime getAplicaDesde() {
		return aplicaDesde;
	}

	public String getFrenadoPor() {
		return frenadoPor;
	}

	public LocalDateTime getFrenadoEn() {
		return frenadoEn;
	}

	public String getMotivoFreno() {
		return motivoFreno;
	}

	public boolean estaFrenada() {
		return frenadoPor != null;
	}

	/**
	 * Tanda 1c (A1c-5): la huella de la acción que autoriza el código, igual que la calcula la base
	 * ({@code SHA2(CONCAT_WS('|', tipo, entidad, entidad_id, datos), 256)}).
	 */
	public String huellaAccion() {
		return huella(tipo, entidad, entidadId, datos);
	}

	/** La misma huella, antes de que la solicitud exista. */
	public static String huella(TipoSolicitud tipo, String entidad, Long entidadId, String datos) {
		return pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256.hex(tipo.name() + "|" + entidad + "|" + entidadId + "|"
				+ datos);
	}
}
