package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Un alumno en una sección de un año. Una por alumno y año, y la sección es de ese año (ambas cosas también las
 * exige la base). El alumno y el año no cambian; la sección sí (en el mismo año).
 */
@Entity
@Table(name = "matricula")
public class Matricula extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alumno_id", nullable = false, updatable = false)
	private Alumno alumno;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anio_escolar_id", nullable = false, updatable = false)
	private AnioEscolar anioEscolar;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seccion_id", nullable = false)
	private Seccion seccion;

	@Column(name = "fecha_matricula", nullable = false, updatable = false)
	private LocalDate fechaMatricula;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoMatricula estado;

	@Column(name = "retirada_en")
	private LocalDate retiradaEn;

	protected Matricula() {
		// requerido por JPA
	}

	public static Matricula nueva(Alumno alumno, Seccion seccion, LocalDate fecha) {
		Matricula matricula = new Matricula();
		matricula.alumno = Objects.requireNonNull(alumno, "alumno");
		matricula.seccion = Objects.requireNonNull(seccion, "seccion");
		matricula.anioEscolar = seccion.getAnioEscolar();
		matricula.fechaMatricula = Objects.requireNonNull(fecha, "fecha");
		matricula.estado = EstadoMatricula.ACTIVA;
		return matricula;
	}

	/**
	 * Cambia de sección dentro del mismo año.
	 *
	 * @param permitirOtroNivel {@code false} si la matrícula ya tiene cuotas: un cambio de nivel cambiaría la pensión
	 */
	public void cambiarSeccion(Seccion nueva, boolean permitirOtroNivel) {
		Objects.requireNonNull(nueva, "nueva");
		if (estado != EstadoMatricula.ACTIVA) {
			throw new ReglaNegocioException("La matrícula está retirada: no se cambia de sección.");
		}
		if (!nueva.getAnioEscolar().getId().equals(anioEscolar.getId())) {
			throw new ReglaNegocioException("La sección nueva debe ser del mismo año (" + anioEscolar.getAnio() + ").");
		}
		if (nueva.getId() != null && nueva.getId().equals(seccion.getId())) {
			throw new ReglaNegocioException("El alumno ya está en " + seccion.etiqueta() + ".");
		}
		if (!nueva.isActiva()) {
			throw new ReglaNegocioException("La sección " + nueva.etiqueta() + " está desactivada.");
		}
		if (nueva.nivel() != seccion.nivel() && !permitirOtroNivel) {
			throw new ReglaNegocioException("El alumno ya tiene cuotas de " + seccion.nivel().etiqueta()
					+ ": no se cambia a " + nueva.nivel().etiqueta() + " porque cambiaría su pensión.");
		}
		seccion = nueva;
	}

	public void retirar(LocalDate fecha) {
		if (estado == EstadoMatricula.RETIRADA) {
			return;
		}
		estado = EstadoMatricula.RETIRADA;
		retiradaEn = Objects.requireNonNull(fecha, "fecha");
	}

	public Nivel nivel() {
		return seccion.nivel();
	}

	public boolean activa() {
		return estado == EstadoMatricula.ACTIVA;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public AnioEscolar getAnioEscolar() {
		return anioEscolar;
	}

	public Seccion getSeccion() {
		return seccion;
	}

	public LocalDate getFechaMatricula() {
		return fechaMatricula;
	}

	public EstadoMatricula getEstado() {
		return estado;
	}

	public LocalDate getRetiradaEn() {
		return retiradaEn;
	}
}
