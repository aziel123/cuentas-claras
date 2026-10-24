package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.util.Objects;

/**
 * Sprint 10, tanda 1 (sección 3.4; A2): un área del catálogo que califica el docente de una asignación TUTOR o DOCENTE. Es
 * el poder de poner notas, así que lo deciden dos personas: SOLO INSERCIÓN, solo mientras la asignación está PROPUESTA y
 * todavía sin su solicitud enlazada, por quien la propuso, en la misma transacción de la propuesta; la aprueba otra
 * persona junto con la asignación (y la base compara las áreas con las aprobadas al pasarla a VIGENTE). Cambiar las áreas
 * de una asignación vigente es proponer otra igual. Nada cambia ni se borra ({@code updatable = false}; sin GRANT de
 * UPDATE ni DELETE: 1142). Una cobertura y el auxiliar no llevan áreas: no califican.
 */
@Entity
@Table(name = "asignacion_area")
public class AsignacionArea extends BaseEntity {

	@Column(name = "asignacion_id", nullable = false, updatable = false)
	private Long asignacionId;

	@Column(name = "area_id", nullable = false, updatable = false)
	private Long areaId;

	protected AsignacionArea() {
		// requerido por JPA
	}

	public static AsignacionArea de(Long asignacionId, Long areaId) {
		AsignacionArea a = new AsignacionArea();
		a.asignacionId = Objects.requireNonNull(asignacionId, "asignacionId");
		a.areaId = Objects.requireNonNull(areaId, "areaId");
		return a;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("Las áreas de una asignación no se cambian: se propone otra asignación.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las áreas de una asignación no se borran.");
	}

	public Long getAsignacionId() {
		return asignacionId;
	}

	public Long getAreaId() {
		return areaId;
	}
}
