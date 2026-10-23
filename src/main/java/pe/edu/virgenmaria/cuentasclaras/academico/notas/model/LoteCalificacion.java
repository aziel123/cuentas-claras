package pe.edu.virgenmaria.cuentasclaras.academico.notas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 10, tanda 2 (sección 3.5, H3): un guardado del docente (un POST, un lote, una firma
 * {@code lote_calificacion:{periodo}:{seccion}:{area}:{secuencia}}). SOLO INSERCIÓN: nada cambia ni se borra
 * ({@code updatable = false}; sin GRANT de UPDATE ni DELETE: 1142). Con el periodo CERRADO lleva su CALIFICACION_TARDIA
 * aprobada. {@code registrado_bd} lo pone la base (trg_lote_calificacion_nace).
 */
@Entity
@Table(name = "lote_calificacion")
public class LoteCalificacion extends BaseEntity {

	@Column(name = "periodo_id", nullable = false, updatable = false)
	private Long periodoId;

	@Column(name = "seccion_id", nullable = false, updatable = false)
	private Long seccionId;

	@Column(name = "area_id", nullable = false, updatable = false)
	private Long areaId;

	@Column(nullable = false, updatable = false)
	private Integer secuencia;

	@Column(name = "solicitud_id", updatable = false)
	private Long solicitudId;

	@Column(name = "sesion_id", nullable = false, updatable = false)
	private Long sesionId;

	@Column(nullable = false, updatable = false)
	private Integer filas;

	@Column(name = "registrado_bd", updatable = false)
	private LocalDateTime registradoBd;

	protected LoteCalificacion() {
		// requerido por JPA
	}

	public static LoteCalificacion nuevo(Long periodoId, Long seccionId, Long areaId, int secuencia, Long solicitudId,
			Long sesionId, int filas, LocalDateTime ahora) {
		LoteCalificacion l = new LoteCalificacion();
		l.periodoId = Objects.requireNonNull(periodoId, "periodoId");
		l.seccionId = Objects.requireNonNull(seccionId, "seccionId");
		l.areaId = Objects.requireNonNull(areaId, "areaId");
		l.secuencia = secuencia;
		l.solicitudId = solicitudId;
		l.sesionId = Objects.requireNonNull(sesionId, "sesionId");
		l.filas = filas;
		l.registradoBd = ahora;
		return l;
	}

	@PreUpdate
	void impedirCambio() {
		throw new IllegalStateException("Un lote de notas no se cambia: es de solo inserción.");
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Un lote de notas no se borra.");
	}

	public Long getPeriodoId() {
		return periodoId;
	}

	public Long getSeccionId() {
		return seccionId;
	}

	public Long getAreaId() {
		return areaId;
	}

	public int getSecuencia() {
		return secuencia;
	}

	public Long getSolicitudId() {
		return solicitudId;
	}

	public Long getSesionId() {
		return sesionId;
	}

	public int getFilas() {
		return filas;
	}
}
