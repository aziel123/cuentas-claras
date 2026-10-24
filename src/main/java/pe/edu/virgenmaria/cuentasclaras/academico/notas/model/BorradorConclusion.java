package pe.edu.virgenmaria.cuentasclaras.academico.notas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 10, tanda 2 (M8): el texto de la conclusión que propone una corrección de nota. SOLO INSERCIÓN. Nunca va en
 * {@code solicitud_cambio.datos} ni en la bitácora (que llevan su id y su SHA-256): puede tener datos sensibles (apoyos y
 * ajustes por discapacidad). Lo leen solo quien lo escribió y quien resuelve la solicitud, con registro en
 * {@code acceso_dato_personal}.
 */
@Entity
@Table(name = "borrador_conclusion")
public class BorradorConclusion extends BaseEntity {

	@Column(name = "calificacion_id", nullable = false, updatable = false)
	private Long calificacionId;

	@Column(nullable = false, updatable = false, length = 1000)
	private String texto;

	@Column(nullable = false, updatable = false, length = 64)
	private String sha256;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	protected BorradorConclusion() {
		// requerido por JPA
	}

	public static BorradorConclusion de(Long calificacionId, String texto, Long sesionId) {
		BorradorConclusion b = new BorradorConclusion();
		b.calificacionId = Objects.requireNonNull(calificacionId, "calificacion");
		b.texto = Objects.requireNonNull(texto, "texto");
		b.sha256 = HuellaConclusion.de(texto);
		b.sesionId = Objects.requireNonNull(sesionId, "sesion");
		return b;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("Un borrador de conclusión no se cambia.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Un borrador de conclusión no se borra.");
	}

	public Long getCalificacionId() {
		return calificacionId;
	}

	public String getTexto() {
		return texto;
	}

	public String getSha256() {
		return sha256;
	}
}
