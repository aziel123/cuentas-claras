package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * Correcciones del sprint 10 (V38; hallazgo 8 y P-1, P-6 y P-7 del QA): un cambio de sección o de estado de una matrícula,
 * que escribe SOLO la base (trg_matricula_estado, con su hora de Lima). La aplicación solo la lee: la libreta de un periodo
 * es de la sección y el estado que la matrícula tenía a su {@code cerrado_en}. Con H2 (sin triggers) no hay filas y vale lo
 * de hoy.
 */
@Entity
@Table(name = "matricula_historial")
public class HistorialMatricula extends BaseEntity {

	@Column(name = "matricula_id", nullable = false, insertable = false, updatable = false)
	private Long matriculaId;

	@Column(name = "seccion_anterior_id", nullable = false, insertable = false, updatable = false)
	private Long seccionAnteriorId;

	@Column(name = "seccion_nueva_id", nullable = false, insertable = false, updatable = false)
	private Long seccionNuevaId;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_anterior", nullable = false, insertable = false, updatable = false, length = 20)
	private EstadoMatricula estadoAnterior;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_nuevo", nullable = false, insertable = false, updatable = false, length = 20)
	private EstadoMatricula estadoNuevo;

	@Column(name = "cambiado_bd", nullable = false, insertable = false, updatable = false)
	private LocalDateTime cambiadoBd;

	protected HistorialMatricula() {
		// requerido por JPA
	}

	@PrePersist
	void impedirAlta() {
		throw new IllegalStateException("La historia de la matrícula la escribe la base.");
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("La historia de la matrícula no se cambia.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("La historia de la matrícula no se borra.");
	}

	public Long getMatriculaId() {
		return matriculaId;
	}

	public Long getSeccionAnteriorId() {
		return seccionAnteriorId;
	}

	public Long getSeccionNuevaId() {
		return seccionNuevaId;
	}

	public EstadoMatricula getEstadoAnterior() {
		return estadoAnterior;
	}

	public EstadoMatricula getEstadoNuevo() {
		return estadoNuevo;
	}

	public LocalDateTime getCambiadoBd() {
		return cambiadoBd;
	}
}
