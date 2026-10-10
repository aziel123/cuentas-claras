package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Una sesión de una persona en la base (sprint 7, tanda 2; sección 3.4). Guarda SOLO el SHA-256 del secreto de la sesión;
 * el secreto vive en la memoria del servidor (la sesión HTTP) y firma cada aprobación ({@link FirmaOperacion}). La abre y
 * la cierra solo {@code cc_sistema} (1142 para {@code cc_app}); en MySQL nace ahora, de una cuenta activa, con 12 horas como
 * máximo (trg_sesion_usuario_nace) y se cierra una sola vez (trg_sesion_usuario_cierre). Nunca se borra.
 * <p>
 * Solo {@code cerradaEn}, {@code motivoCierre}, {@code actualizadoEn} y {@code version} son actualizables: coinciden con
 * el GRANT por columna de {@code cc_sistema}.
 */
@Entity
@Table(name = "sesion_usuario")
public class SesionUsuario extends BaseEntity {

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "hash_token", nullable = false, updatable = false, length = 64)
	private String hashToken;

	@Column(updatable = false, length = 45)
	private String ip;

	@Column(name = "abierta_en", nullable = false, updatable = false)
	private LocalDateTime abiertaEn;

	@Column(name = "vence_en", nullable = false, updatable = false)
	private LocalDateTime venceEn;

	@Column(name = "cerrada_en")
	private LocalDateTime cerradaEn;

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_cierre", length = 20)
	private MotivoCierreSesion motivoCierre;

	protected SesionUsuario() {
		// requerido por JPA
	}

	public static SesionUsuario abrir(Long usuarioId, String hashToken, String ip, LocalDateTime abiertaEn,
			LocalDateTime venceEn) {
		SesionUsuario s = new SesionUsuario();
		s.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		if (hashToken == null || !hashToken.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("El hash del secreto de la sesión no es un SHA-256");
		}
		s.hashToken = hashToken;
		s.ip = ip == null || ip.length() > 45 ? null : ip;
		s.abiertaEn = Objects.requireNonNull(abiertaEn, "abiertaEn");
		s.venceEn = Objects.requireNonNull(venceEn, "venceEn");
		if (!venceEn.isAfter(abiertaEn)) {
			throw new IllegalArgumentException("La sesión vence después de abrirse");
		}
		return s;
	}

	/** Se cierra una sola vez (si ya estaba cerrada, no cambia nada). */
	public boolean cerrar(MotivoCierreSesion motivo, LocalDateTime ahora) {
		if (cerradaEn != null) {
			return false;
		}
		motivoCierre = Objects.requireNonNull(motivo, "motivo");
		cerradaEn = Objects.requireNonNull(ahora, "ahora");
		return true;
	}

	/** Abierta y sin vencer en {@code ahora}. */
	public boolean vigente(LocalDateTime ahora) {
		return cerradaEn == null && ahora.isBefore(venceEn);
	}

	@PreRemove
	void impedirBorrado() {
		throw new UnsupportedOperationException("Las sesiones no se borran: se cierran");
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getHashToken() {
		return hashToken;
	}

	public String getIp() {
		return ip;
	}

	public LocalDateTime getAbiertaEn() {
		return abiertaEn;
	}

	public LocalDateTime getVenceEn() {
		return venceEn;
	}

	public LocalDateTime getCerradaEn() {
		return cerradaEn;
	}

	public MotivoCierreSesion getMotivoCierre() {
		return motivoCierre;
	}
}
