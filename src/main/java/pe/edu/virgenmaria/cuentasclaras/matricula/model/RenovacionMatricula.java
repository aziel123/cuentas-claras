package pe.edu.virgenmaria.cuentasclaras.matricula.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sprint 5, tanda 2: propuesta del sistema para que un alumno continúe el año siguiente (grado siguiente, sección de la
 * misma letra). Sin respuesta de la familia no hay deuda: vence. Al confirmarse, {@code sistema.matricula} crea la
 * matrícula RESERVADA (con su cuota de matrícula del plan aprobado) y la renovación pasa a MATRICULADA.
 * <p>
 * El alumno, la familia, el año destino y la matrícula de origen no cambian (no tienen GRANT de UPDATE: 1143). El grado y
 * la sección solo cambian en PROPUESTA, y la respuesta se escribe una vez (triggers en MySQL).
 */
@Entity
@Table(name = "renovacion_matricula")
public class RenovacionMatricula extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anio_destino_id", nullable = false, updatable = false)
	private AnioEscolar anioDestino;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alumno_id", nullable = false, updatable = false)
	private Alumno alumno;

	@Column(name = "familia_id", nullable = false, updatable = false)
	private Long familiaId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "matricula_origen_id", nullable = false, updatable = false)
	private Matricula matriculaOrigen;

	@Enumerated(EnumType.STRING)
	@Column(name = "grado_destino", nullable = false, length = 20)
	private Grado gradoDestino;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seccion_destino_id", nullable = false)
	private Seccion seccionDestino;

	/** Solo informativo (decisión 55): saldos vencidos al abrir la campaña. No bloquea la renovación. */
	@Column(name = "deuda_al_proponer", nullable = false, precision = 10, scale = 2, updatable = false)
	private BigDecimal deudaAlProponer;

	@Column(name = "vence_en", nullable = false, updatable = false)
	private LocalDate venceEn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoRenovacion estado;

	@Enumerated(EnumType.STRING)
	@Column(name = "canal_respuesta", length = 12)
	private CanalRespuesta canalRespuesta;

	@Column(name = "respondido_por", length = 60)
	private String respondidoPor;

	@Column(name = "respondido_en")
	private LocalDateTime respondidoEn;

	@Column(name = "matricula_id")
	private Long matriculaId;

	protected RenovacionMatricula() {
		// requerido por JPA
	}

	public static RenovacionMatricula propuesta(Matricula origen, Seccion destino, BigDecimal deuda, LocalDate venceEn) {
		Objects.requireNonNull(origen, "origen");
		Objects.requireNonNull(destino, "destino");
		RenovacionMatricula renovacion = new RenovacionMatricula();
		renovacion.matriculaOrigen = origen;
		renovacion.alumno = origen.getAlumno();
		renovacion.familiaId = origen.getAlumno().getFamilia().getId();
		renovacion.anioDestino = destino.getAnioEscolar();
		renovacion.gradoDestino = destino.getGrado();
		renovacion.seccionDestino = destino;
		renovacion.deudaAlProponer = (deuda == null ? BigDecimal.ZERO : deuda).setScale(2, RoundingMode.HALF_UP);
		renovacion.venceEn = Objects.requireNonNull(venceEn, "venceEn");
		renovacion.estado = EstadoRenovacion.PROPUESTA;
		return renovacion;
	}

	/** Dirección: repitencia o cambio de sección, solo mientras la familia no haya respondido. */
	public void cambiarDestino(Seccion seccion) {
		Objects.requireNonNull(seccion, "seccion");
		if (estado != EstadoRenovacion.PROPUESTA) {
			throw new ReglaNegocioException("La familia ya respondió: el grado y la sección ya no cambian.");
		}
		if (!seccion.getAnioEscolar().getId().equals(anioDestino.getId())) {
			throw new ReglaNegocioException("La sección debe ser del año " + anioDestino.getAnio() + ".");
		}
		if (!seccion.isActiva()) {
			throw new ReglaNegocioException("La sección " + seccion.etiqueta() + " está desactivada.");
		}
		seccionDestino = seccion;
		gradoDestino = seccion.getGrado();
	}

	/** La respuesta de la familia (en el portal) o la que registra Administración en persona. Se escribe una vez. */
	public void responder(boolean continua, CanalRespuesta canal, String quien, LocalDateTime ahora) {
		if (estado != EstadoRenovacion.PROPUESTA) {
			throw new ReglaNegocioException(estado == EstadoRenovacion.VENCIDA
					? "La fecha para responder ya pasó. Comunícate con el colegio."
					: "Esta renovación ya tiene respuesta.");
		}
		if (ahora.toLocalDate().isAfter(venceEn)) {
			throw new ReglaNegocioException("La fecha para responder ya pasó. Comunícate con el colegio.");
		}
		estado = continua ? EstadoRenovacion.CONFIRMADA : EstadoRenovacion.NO_CONTINUA;
		canalRespuesta = Objects.requireNonNull(canal, "canal");
		respondidoPor = Objects.requireNonNull(quien, "quien");
		respondidoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** {@code sistema.matricula} creó la matrícula RESERVADA. */
	public void matricular(Long matriculaId) {
		if (estado != EstadoRenovacion.CONFIRMADA) {
			throw new ReglaNegocioException("Solo se matricula una renovación confirmada.");
		}
		this.matriculaId = Objects.requireNonNull(matriculaId, "matriculaId");
		estado = EstadoRenovacion.MATRICULADA;
	}

	/** Pasó la fecha límite sin respuesta: no hay deuda. */
	public void vencer() {
		if (estado == EstadoRenovacion.PROPUESTA) {
			estado = EstadoRenovacion.VENCIDA;
		}
	}

	public boolean conDeuda() {
		return deudaAlProponer.signum() > 0;
	}

	public AnioEscolar getAnioDestino() {
		return anioDestino;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public Long getFamiliaId() {
		return familiaId;
	}

	public Matricula getMatriculaOrigen() {
		return matriculaOrigen;
	}

	public Grado getGradoDestino() {
		return gradoDestino;
	}

	public Seccion getSeccionDestino() {
		return seccionDestino;
	}

	public BigDecimal getDeudaAlProponer() {
		return deudaAlProponer;
	}

	public LocalDate getVenceEn() {
		return venceEn;
	}

	public EstadoRenovacion getEstado() {
		return estado;
	}

	public CanalRespuesta getCanalRespuesta() {
		return canalRespuesta;
	}

	public String getRespondidoPor() {
		return respondidoPor;
	}

	public LocalDateTime getRespondidoEn() {
		return respondidoEn;
	}

	public Long getMatriculaId() {
		return matriculaId;
	}
}
