package pe.edu.virgenmaria.cuentasclaras.comunicados.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 9, tanda 3 (secciones 3.8 y 5): un comunicado a las familias. Nace COMPLETO al enviarse: título, texto, alcance,
 * emisor, urgente, confirmación, adjunto, sesión y huella NO cambian (sin GRANT de UPDATE: 1143; aquí,
 * {@code updatable = false}). Se corrige con una fe de erratas (otro comunicado) y se retira sin borrarse. Solo cambian el
 * estado y lo escrito una vez del envío ({@code enviado_en}, {@code destinatarios}, {@code solicitud_id}) y del retiro.
 */
@Entity
@Table(name = "comunicado")
public class Comunicado extends BaseEntity {

	public static final int TITULO_MIN = 5;

	public static final int TITULO_MAX = 120;

	public static final int CUERPO_MIN = 10;

	public static final int CUERPO_MAX = 4000;

	public static final int MOTIVO_MIN = 10;

	public static final int MOTIVO_MAX = 300;

	@Column(name = "anio_escolar_id", nullable = false, updatable = false)
	private Long anioEscolarId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 14)
	private TipoComunicado tipo;

	@Column(name = "corrige_id", updatable = false)
	private Long corrigeId;

	@Column(name = "emisor_usuario_id", nullable = false, updatable = false)
	private Long emisorUsuarioId;

	@Enumerated(EnumType.STRING)
	@Column(name = "emisor_rol", nullable = false, updatable = false, length = 12)
	private EmisorRol emisorRol;

	@Column(name = "emisor_etiqueta", nullable = false, updatable = false, length = 120)
	private String emisorEtiqueta;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private AlcanceComunicado alcance;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 12)
	private Nivel nivel;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 20)
	private Grado grado;

	@Column(name = "seccion_id", updatable = false)
	private Long seccionId;

	@Column(nullable = false, updatable = false, length = TITULO_MAX)
	private String titulo;

	@Column(nullable = false, updatable = false, length = CUERPO_MAX)
	private String cuerpo;

	@Column(nullable = false, updatable = false)
	private Boolean urgente;

	@Column(name = "requiere_confirmacion", nullable = false, updatable = false)
	private Boolean requiereConfirmacion;

	@Column(name = "adjunto_id", updatable = false)
	private Long adjuntoId;

	@Column(name = "huella_contenido", nullable = false, updatable = false, length = 64)
	private String huellaContenido;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private EstadoComunicado estado;

	@Column(name = "solicitud_id")
	private Long solicitudId;

	@Column(name = "enviado_en")
	private LocalDateTime enviadoEn;

	private Integer destinatarios;

	@Column(name = "retirado_por", length = 60)
	private String retiradoPor;

	@Column(name = "retirado_en")
	private LocalDateTime retiradoEn;

	@Column(name = "motivo_retiro", length = MOTIVO_MAX)
	private String motivoRetiro;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	protected Comunicado() {
		// requerido por JPA
	}

	/** El contenido de un comunicado, ya revisado (el emisor y su etiqueta los arma el sistema). */
	public record Contenido(AlcanceComunicado alcance, Nivel nivel, Grado grado, Long seccionId, String titulo,
			String cuerpo, boolean urgente, boolean requiereConfirmacion) {

		public Contenido {
			Objects.requireNonNull(alcance, "alcance");
			Objects.requireNonNull(titulo, "titulo");
			Objects.requireNonNull(cuerpo, "cuerpo");
		}
	}

	/** El emisor tal como lo decide el sistema desde la sesión (E16). */
	public record Emisor(Long usuarioId, EmisorRol rol, String etiqueta, Long sesionId) {

		public Emisor {
			Objects.requireNonNull(usuarioId, "usuarioId");
			Objects.requireNonNull(rol, "rol");
			Objects.requireNonNull(etiqueta, "etiqueta");
			Objects.requireNonNull(sesionId, "sesionId");
		}
	}

	/**
	 * Un comunicado nuevo: ENVIADO (con la hora del envío) o POR_APROBAR (PDF de un docente o PDF que requiere revisión,
	 * B3). {@code corrigeId} solo en una fe de erratas.
	 */
	public static Comunicado nuevo(Long anioEscolarId, Long corrigeId, Emisor emisor, Contenido contenido,
			Long adjuntoId, String huella, boolean porAprobar, LocalDateTime ahora) {
		Comunicado c = new Comunicado();
		c.anioEscolarId = Objects.requireNonNull(anioEscolarId, "anioEscolarId");
		c.tipo = corrigeId == null ? TipoComunicado.NORMAL : TipoComunicado.FE_DE_ERRATAS;
		c.corrigeId = corrigeId;
		c.emisorUsuarioId = emisor.usuarioId();
		c.emisorRol = emisor.rol();
		c.emisorEtiqueta = emisor.etiqueta();
		c.sesionId = emisor.sesionId();
		c.alcance = contenido.alcance();
		c.nivel = contenido.alcance() == AlcanceComunicado.NIVEL ? contenido.nivel() : null;
		c.grado = contenido.alcance() == AlcanceComunicado.GRADO ? contenido.grado() : null;
		c.seccionId = contenido.alcance() == AlcanceComunicado.SECCION ? contenido.seccionId() : null;
		c.titulo = contenido.titulo();
		c.cuerpo = contenido.cuerpo();
		c.urgente = contenido.urgente();
		c.requiereConfirmacion = contenido.requiereConfirmacion();
		c.adjuntoId = adjuntoId;
		c.huellaContenido = Objects.requireNonNull(huella, "huella");
		c.estado = porAprobar ? EstadoComunicado.POR_APROBAR : EstadoComunicado.ENVIADO;
		c.enviadoEn = porAprobar ? null : Objects.requireNonNull(ahora, "ahora");
		return c;
	}

	/** Liga la solicitud PUBLICAR_COMUNICADO (una sola vez). */
	public void ligarSolicitud(Long solicitud) {
		if (estado != EstadoComunicado.POR_APROBAR || solicitudId != null) {
			throw new ReglaNegocioException("Este comunicado ya tiene su solicitud.");
		}
		this.solicitudId = Objects.requireNonNull(solicitud, "solicitud");
	}

	/** POR_APROBAR → ENVIADO, con la aprobación del otro órgano (la base exige la solicitud firmada). */
	public void aprobar(LocalDateTime ahora) {
		if (estado != EstadoComunicado.POR_APROBAR) {
			throw new ReglaNegocioException("Este comunicado ya no está por aprobar.");
		}
		this.estado = EstadoComunicado.ENVIADO;
		this.enviadoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** POR_APROBAR → RECHAZADO, con la solicitud rechazada. */
	public void rechazar() {
		if (estado != EstadoComunicado.POR_APROBAR) {
			throw new ReglaNegocioException("Este comunicado ya no está por aprobar.");
		}
		this.estado = EstadoComunicado.RECHAZADO;
	}

	/** Cierra la lista de destinatarios (una sola vez; la base la compara con sus filas y, en una fe de erratas, B1). */
	public void cerrarDestinatarios(int cantidad) {
		if (estado != EstadoComunicado.ENVIADO || destinatarios != null) {
			throw new ReglaNegocioException("La lista de destinatarios ya se cerró.");
		}
		this.destinatarios = cantidad;
	}

	/** ENVIADO → RETIRADO, por Dirección o Promotoría con su firma y motivo. Nada se borra. */
	public void retirar(String quien, String motivo, LocalDateTime ahora) {
		if (estado != EstadoComunicado.ENVIADO) {
			throw new ReglaNegocioException("Solo se retira un comunicado enviado.");
		}
		String limpio = motivo == null ? "" : motivo.strip();
		if (limpio.length() < MOTIVO_MIN || limpio.length() > MOTIVO_MAX) {
			throw new ReglaNegocioException("Escribe el motivo del retiro (de " + MOTIVO_MIN + " a " + MOTIVO_MAX
					+ " caracteres): lo verán las familias.");
		}
		this.estado = EstadoComunicado.RETIRADO;
		this.retiradoPor = Objects.requireNonNull(quien, "quien");
		this.retiradoEn = Objects.requireNonNull(ahora, "ahora");
		this.motivoRetiro = limpio;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Un comunicado no se borra: se retira.");
	}

	public Long getAnioEscolarId() {
		return anioEscolarId;
	}

	public TipoComunicado getTipo() {
		return tipo;
	}

	public Long getCorrigeId() {
		return corrigeId;
	}

	public Long getEmisorUsuarioId() {
		return emisorUsuarioId;
	}

	public EmisorRol getEmisorRol() {
		return emisorRol;
	}

	public String getEmisorEtiqueta() {
		return emisorEtiqueta;
	}

	public AlcanceComunicado getAlcance() {
		return alcance;
	}

	public Nivel getNivel() {
		return nivel;
	}

	public Grado getGrado() {
		return grado;
	}

	public Long getSeccionId() {
		return seccionId;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getCuerpo() {
		return cuerpo;
	}

	public boolean isUrgente() {
		return urgente;
	}

	public boolean isRequiereConfirmacion() {
		return requiereConfirmacion;
	}

	public Long getAdjuntoId() {
		return adjuntoId;
	}

	public String getHuellaContenido() {
		return huellaContenido;
	}

	public EstadoComunicado getEstado() {
		return estado;
	}

	public Long getSolicitudId() {
		return solicitudId;
	}

	public LocalDateTime getEnviadoEn() {
		return enviadoEn;
	}

	public Integer getDestinatarios() {
		return destinatarios;
	}

	public String getRetiradoPor() {
		return retiradoPor;
	}

	public LocalDateTime getRetiradoEn() {
		return retiradoEn;
	}

	public String getMotivoRetiro() {
		return motivoRetiro;
	}

	public Long getSesionId() {
		return sesionId;
	}

	public boolean esFeDeErratas() {
		return tipo == TipoComunicado.FE_DE_ERRATAS;
	}
}
