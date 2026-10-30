package pe.edu.virgenmaria.cuentasclaras.colegio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDate;

/**
 * Año escolar del colegio. Solo uno EN_CURSO: {@code vigente} vale TRUE en ese año y NULL en los demás, y la base
 * tiene un UNIQUE (colegio_id, vigente) más un CHECK que lo ata al estado.
 */
@Entity
@Table(name = "anio_escolar")
public class AnioEscolar extends BaseEntity {

	public static final int ANIO_MINIMO = 2000;

	public static final int ANIO_MAXIMO = 2100;

	@Column(nullable = false, updatable = false)
	private int anio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoAnioEscolar estado;

	@Column
	private Boolean vigente;

	@Column(name = "inicio_clases", nullable = false)
	private LocalDate inicioClases;

	@Column(name = "fin_clases", nullable = false)
	private LocalDate finClases;

	/**
	 * Sprint 11, tanda 1 (AP-9): quién firmó el último cambio. En MySQL, trg_anio_escolar_cambio exige que sea Promotoría
	 * o Dirección activa con su firma de la versión anterior ({@code anio_escolar:{id}:{version}}).
	 */
	@Column(name = "actualizado_por", length = 60)
	private String actualizadoPor;

	protected AnioEscolar() {
		// requerido por JPA
	}

	public static AnioEscolar nuevo(int anio, boolean enCurso, LocalDate inicioClases, LocalDate finClases) {
		if (anio < ANIO_MINIMO || anio > ANIO_MAXIMO) {
			throw new ReglaNegocioException("El año debe estar entre " + ANIO_MINIMO + " y " + ANIO_MAXIMO + ".");
		}
		if (inicioClases == null || finClases == null) {
			throw new ReglaNegocioException("Indica el inicio y el fin de clases.");
		}
		if (!inicioClases.isBefore(finClases)) {
			throw new ReglaNegocioException("El inicio de clases debe ser antes del fin de clases.");
		}
		if (inicioClases.getYear() != anio || finClases.getYear() != anio) {
			throw new ReglaNegocioException("Las clases del año " + anio + " deben empezar y terminar en " + anio + ".");
		}
		AnioEscolar nuevo = new AnioEscolar();
		nuevo.anio = anio;
		nuevo.inicioClases = inicioClases;
		nuevo.finClases = finClases;
		nuevo.estado = EstadoAnioEscolar.PLANIFICADO;
		if (enCurso) {
			nuevo.iniciar();
		}
		return nuevo;
	}

	/**
	 * Sprint 11, tanda 1 (sección 3.7, AP-9): cambia las fechas de clases de un año que no está cerrado, con la firma de
	 * quien lo cambia (Promotoría o Dirección). Las mismas reglas que al crearlo.
	 */
	public void cambiarFechas(LocalDate inicio, LocalDate fin, String por) {
		if (estado == EstadoAnioEscolar.CERRADO) {
			throw new ReglaNegocioException("El año " + anio + " ya está cerrado: sus fechas no cambian.");
		}
		if (inicio == null || fin == null) {
			throw new ReglaNegocioException("Indica el inicio y el fin de clases.");
		}
		if (!inicio.isBefore(fin)) {
			throw new ReglaNegocioException("El inicio de clases debe ser antes del fin de clases.");
		}
		if (inicio.getYear() != anio || fin.getYear() != anio) {
			throw new ReglaNegocioException("Las clases del año " + anio + " deben empezar y terminar en " + anio + ".");
		}
		if (inicio.equals(inicioClases) && fin.equals(finClases)) {
			throw new ReglaNegocioException("Las fechas son las mismas: no hay nada que cambiar.");
		}
		this.inicioClases = inicio;
		this.finClases = fin;
		this.actualizadoPor = java.util.Objects.requireNonNull(por, "por");
	}

	public String getActualizadoPor() {
		return actualizadoPor;
	}

	public boolean enCurso() {
		return estado == EstadoAnioEscolar.EN_CURSO;
	}

	public boolean cerrado() {
		return estado == EstadoAnioEscolar.CERRADO;
	}

	/** Pasa a EN_CURSO. Que no haya otro lo comprueba el servicio y lo garantiza la base. */
	public void iniciar() {
		if (estado == EstadoAnioEscolar.CERRADO) {
			throw new ReglaNegocioException("El año " + anio + " ya está cerrado.");
		}
		estado = EstadoAnioEscolar.EN_CURSO;
		vigente = Boolean.TRUE;
	}

	public void cerrar() {
		estado = EstadoAnioEscolar.CERRADO;
		vigente = null;
	}

	/**
	 * Fecha de matrícula si no se indica otra: el inicio de clases (cronograma completo). Una fecha posterior (ingreso
	 * tardío) recorta pensiones y necesita la aprobación de otra persona (auditoría antifraude, A3).
	 */
	public LocalDate fechaMatriculaPorDefecto() {
		return inicioClases;
	}

	/** {@code true} si la fecha es posterior al inicio de clases: un ingreso tardío. */
	public boolean ingresoTardio(LocalDate fecha) {
		return fecha != null && fecha.isAfter(inicioClases);
	}

	/** Rango aceptado para una fecha de matrícula: del 01/07 del año anterior al fin de clases. */
	public LocalDate primeraFechaDeMatricula() {
		return LocalDate.of(anio - 1, 7, 1);
	}

	public String rangoClases() {
		return Calendario.formatear(inicioClases) + " al " + Calendario.formatear(finClases);
	}

	public int getAnio() {
		return anio;
	}

	public EstadoAnioEscolar getEstado() {
		return estado;
	}

	public LocalDate getInicioClases() {
		return inicioClases;
	}

	public LocalDate getFinClases() {
		return finClases;
	}
}
