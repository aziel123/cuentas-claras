package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * La primera Dirección de la historia de un colegio (V27, correcciones del sprint 7, observación de QA). La excepción de
 * arranque (dar DIRECTOR sin solicitud cuando hay una sola Promotoría y ninguna Dirección activa) se usa UNA vez por
 * colegio: esta fila la marca ({@code uk_primera_direccion_colegio}). La escribe solo {@code cc_sistema} (GRANT), en la
 * misma transacción que el rol; en MySQL trg_usuario_rol_alta exige la fila de ESE colegio y de ESA cuenta, recién
 * escrita. De solo inserción.
 */
@Entity
@Table(name = "primera_direccion")
public class PrimeraDireccion extends BaseEntity {

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	protected PrimeraDireccion() {
		// requerido por JPA
	}

	public static PrimeraDireccion de(Long usuarioId) {
		PrimeraDireccion p = new PrimeraDireccion();
		p.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
		return p;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La marca de la primera Dirección no se borra.");
	}

	public Long getUsuarioId() {
		return usuarioId;
	}
}
