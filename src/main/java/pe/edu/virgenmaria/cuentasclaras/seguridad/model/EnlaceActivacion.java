package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Enlace de activación de la cuenta en línea de un apoderado (correcciones del sprint 4, S4-M2): de UN solo uso y con
 * vencimiento. Se guarda solo el SHA-256 del token (quien lee la base no puede usarlo). Al usarlo, el apoderado elige su
 * propia clave; quedan la IP de quien lo creó y la de quien lo usó. Un restablecimiento de Promotoría lo anula. No se
 * borra.
 */
@Entity
@Table(name = "enlace_activacion")
public class EnlaceActivacion extends BaseEntity {

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "hash_token", nullable = false, updatable = false, length = 64)
	private String hashToken;

	@Column(name = "vence_en", nullable = false, updatable = false)
	private LocalDateTime venceEn;

	@Column(name = "creado_ip", updatable = false, length = 45)
	private String creadoIp;

	@Column(name = "usado_en")
	private LocalDateTime usadoEn;

	@Column(name = "usado_ip", length = 45)
	private String usadoIp;

	@Column(name = "anulado_en")
	private LocalDateTime anuladoEn;

	protected EnlaceActivacion() {
		// requerido por JPA
	}

	public static EnlaceActivacion nuevo(Long usuarioId, String hashToken, LocalDateTime venceEn, String creadoIp) {
		EnlaceActivacion e = new EnlaceActivacion();
		e.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		e.hashToken = Objects.requireNonNull(hashToken, "hashToken");
		e.venceEn = Objects.requireNonNull(venceEn, "venceEn");
		e.creadoIp = recortar(creadoIp);
		return e;
	}

	/** Lo usa el apoderado: una sola vez, antes de vencer y si nadie lo anuló. */
	public void usar(LocalDateTime ahora, String ip) {
		if (!vigente(ahora)) {
			throw new ReglaNegocioException("Este enlace ya no sirve (se usó, venció o se reemplazó). Pide al colegio uno "
					+ "nuevo: Promotoría puede restablecer tu acceso.");
		}
		usadoEn = Objects.requireNonNull(ahora, "ahora");
		usadoIp = recortar(ip);
	}

	public void anular(LocalDateTime ahora) {
		if (usadoEn == null && anuladoEn == null) {
			anuladoEn = Objects.requireNonNull(ahora, "ahora");
		}
	}

	public boolean vigente(LocalDateTime ahora) {
		return usadoEn == null && anuladoEn == null && ahora.isBefore(venceEn);
	}

	/** Si quien lo usó lo hizo desde la misma IP de quien lo creó (Promotoría lo revisa con el apoderado). */
	public boolean usadoDesdeLaIpDeQuienLoCreo() {
		return usadoEn != null && creadoIp != null && creadoIp.equals(usadoIp);
	}

	private static String recortar(String ip) {
		if (ip == null) {
			return null;
		}
		return ip.length() <= 45 ? ip : ip.substring(0, 45);
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los enlaces de activación no se borran.");
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getHashToken() {
		return hashToken;
	}

	public LocalDateTime getVenceEn() {
		return venceEn;
	}

	public String getCreadoIp() {
		return creadoIp;
	}

	public LocalDateTime getUsadoEn() {
		return usadoEn;
	}

	public String getUsadoIp() {
		return usadoIp;
	}

	public LocalDateTime getAnuladoEn() {
		return anuladoEn;
	}
}
