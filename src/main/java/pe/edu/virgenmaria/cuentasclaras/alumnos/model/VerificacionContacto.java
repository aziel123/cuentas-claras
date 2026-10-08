package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Correcciones del sprint 5 (S5-A1): enlace de UN solo uso que verifica que un celular o un correo nuevo de un apoderado
 * es de su titular. Nace con su mensaje {@code VERIFICACION_CONTACTO} dentro del proceso de envío (nadie del colegio ve
 * el token); se guarda solo su SHA-256. Se usa una vez, antes de vencer, confirmando el número de documento. No se borra.
 */
@Entity
@Table(name = "verificacion_contacto")
public class VerificacionContacto extends BaseEntity {

	@Column(name = "apoderado_id", nullable = false, updatable = false)
	private Long apoderadoId;

	/** WHATSAPP o CORREO (como el canal del mensaje). */
	@Column(nullable = false, updatable = false, length = 10)
	private String canal;

	@Column(nullable = false, updatable = false, length = 150)
	private String contacto;

	@Column(name = "hash_token", nullable = false, updatable = false, length = 64)
	private String hashToken;

	@Column(name = "mensaje_id", nullable = false, updatable = false)
	private Long mensajeId;

	@Column(name = "vence_en", nullable = false, updatable = false)
	private LocalDateTime venceEn;

	@Column(name = "verificado_en")
	private LocalDateTime verificadoEn;

	@Column(name = "verificado_ip", length = 45)
	private String verificadoIp;

	@Column(name = "anulado_en")
	private LocalDateTime anuladoEn;

	protected VerificacionContacto() {
		// requerido por JPA
	}

	public static VerificacionContacto paraMensaje(Long apoderadoId, boolean whatsapp, String contacto, String hashToken,
			Long mensajeId, LocalDateTime venceEn) {
		VerificacionContacto v = new VerificacionContacto();
		v.apoderadoId = Objects.requireNonNull(apoderadoId, "apoderadoId");
		v.canal = whatsapp ? "WHATSAPP" : "CORREO";
		v.contacto = Objects.requireNonNull(contacto, "contacto");
		v.hashToken = Objects.requireNonNull(hashToken, "hashToken");
		v.mensajeId = Objects.requireNonNull(mensajeId, "mensajeId");
		v.venceEn = Objects.requireNonNull(venceEn, "venceEn");
		return v;
	}

	public boolean vigente(LocalDateTime ahora) {
		return verificadoEn == null && anuladoEn == null && ahora.isBefore(venceEn);
	}

	public void usar(LocalDateTime ahora, String ip) {
		verificadoEn = Objects.requireNonNull(ahora, "ahora");
		verificadoIp = ip == null ? "desconocida" : ip.length() <= 45 ? ip : ip.substring(0, 45);
	}

	/** Otro enlace del mismo contacto lo reemplaza. */
	public void anular(LocalDateTime ahora) {
		if (verificadoEn == null && anuladoEn == null) {
			anuladoEn = Objects.requireNonNull(ahora, "ahora");
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las verificaciones de contacto no se borran.");
	}

	public boolean porWhatsapp() {
		return "WHATSAPP".equals(canal);
	}

	public Long getApoderadoId() {
		return apoderadoId;
	}

	public String getCanal() {
		return canal;
	}

	public String getContacto() {
		return contacto;
	}

	public Long getMensajeId() {
		return mensajeId;
	}

	public LocalDateTime getVenceEn() {
		return venceEn;
	}

	public LocalDateTime getVerificadoEn() {
		return verificadoEn;
	}

	public String getVerificadoIp() {
		return verificadoIp;
	}

	public LocalDateTime getAnuladoEn() {
		return anuladoEn;
	}
}
