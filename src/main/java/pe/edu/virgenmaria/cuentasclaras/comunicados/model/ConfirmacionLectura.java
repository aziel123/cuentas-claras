package pe.edu.virgenmaria.cuentasclaras.comunicados.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 9, tanda 3 (sección 3.10, E23 a E25): «Confirmo que lo leí», una por familia y comunicado, por la cuenta de un
 * apoderado de ESA familia con su sesión (trg_confirmacion_lectura_nace; la FK exige que la familia sea destinataria).
 * SOLO INSERCIÓN (1142). Fuera de la cadena de la bitácora por volumen, como {@code acceso_dato_personal}.
 */
@Entity
@Table(name = "confirmacion_lectura")
public class ConfirmacionLectura extends BaseEntity {

	@Column(name = "comunicado_id", nullable = false, updatable = false)
	private Long comunicadoId;

	@Column(name = "familia_id", nullable = false, updatable = false)
	private Long familiaId;

	@Column(name = "apoderado_id", nullable = false, updatable = false)
	private Long apoderadoId;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	protected ConfirmacionLectura() {
		// requerido por JPA
	}

	public static ConfirmacionLectura de(Long comunicadoId, Long familiaId, Long apoderadoId, Long usuarioId,
			Long sesionId) {
		ConfirmacionLectura c = new ConfirmacionLectura();
		c.comunicadoId = Objects.requireNonNull(comunicadoId, "comunicadoId");
		c.familiaId = Objects.requireNonNull(familiaId, "familiaId");
		c.apoderadoId = Objects.requireNonNull(apoderadoId, "apoderadoId");
		c.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		c.sesionId = Objects.requireNonNull(sesionId, "sesionId");
		return c;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("La confirmación de lectura es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La confirmación de lectura no se borra.");
	}

	public Long getComunicadoId() {
		return comunicadoId;
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public Long getApoderadoId() {
		return apoderadoId;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Long getSesionId() {
		return sesionId;
	}
}
