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
}
