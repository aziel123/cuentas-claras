package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * Sprint 10, tanda 1 (sección 3.3): un periodo de evaluación del año («I bimestre»). Nace PROGRAMADO por Dirección con su
 * firma; abre ({@code sistema.academico} en su fecha o Dirección con su firma antes) y cierra (Dirección con su firma, desde
 * {@code fin − dias_cierre_anticipado}, con el anterior CERRADO). CERRADO no se reabre (decisión 172). En PROGRAMADO cambian
 * las fechas; en ABIERTO el fin solo se extiende (M7). El número y el año no cambian ({@code updatable = false} y 1143).
 * Lo exigen también trg_periodo_evaluacion_nace y _cambio; {@code abierto_en} y {@code cerrado_en} los pone la base en MySQL.
 * Nunca se borra.
 */
@Entity
@Table(name = "periodo_evaluacion")
public class PeriodoEvaluacion extends BaseEntity {

	@Column(name = "anio_escolar_id", nullable = false, updatable = false)
	private Long anioEscolarId;

	@Column(nullable = false, updatable = false)
	private Integer numero;

	@Column(nullable = false, length = 30)
	private String nombre;

	@Column(nullable = false)
	private LocalDate inicio;

	@Column(nullable = false)
	private LocalDate fin;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EstadoPeriodo estado;

	@Column(name = "abierto_por", length = 60)
	private String abiertoPor;

	@Column(name = "abierto_en")
	private LocalDateTime abiertoEn;

	@Column(name = "cerrado_por", length = 60)
	private String cerradoPor;

	@Column(name = "cerrado_en")
	private LocalDateTime cerradoEn;

	@Column(name = "actualizado_por", nullable = false, length = 60)
	private String actualizadoPor;

	protected PeriodoEvaluacion() {
		// requerido por JPA
	}

	/** Un periodo nuevo, PROGRAMADO, a nombre de quien lo programa (Dirección en sesión). */
	public static PeriodoEvaluacion programado(Long anioEscolarId, int numero, String nombre, LocalDate inicio, LocalDate fin,
			String director) {
		PeriodoEvaluacion p = new PeriodoEvaluacion();
		p.anioEscolarId = Objects.requireNonNull(anioEscolarId, "anioEscolarId");
		if (numero < 1 || numero > 4) {
			throw new ReglaNegocioException("El número del periodo va de 1 a 4.");
		}
		p.numero = numero;
		p.nombre = Objects.requireNonNull(nombre, "nombre");
		p.fechas(inicio, fin);
		p.estado = EstadoPeriodo.PROGRAMADO;
		p.actualizadoPor = Objects.requireNonNull(director, "director");
		return p;
	}

	/** Dirección mueve las fechas: el inicio solo en PROGRAMADO; en ABIERTO el fin solo se extiende (M7). */
	public void moverFechas(LocalDate nuevoInicio, LocalDate nuevoFin, String director) {
		if (estado == EstadoPeriodo.CERRADO) {
			throw new ReglaNegocioException("El " + nombre + " está cerrado: sus fechas ya no cambian.");
		}
		if (estado == EstadoPeriodo.ABIERTO && !Objects.equals(nuevoInicio, inicio)) {
			throw new ReglaNegocioException("El " + nombre + " ya abrió: su inicio no cambia (solo se puede extender el fin).");
		}
		if (estado == EstadoPeriodo.ABIERTO && nuevoFin != null && nuevoFin.isBefore(fin)) {
			throw new ReglaNegocioException("El " + nombre + " ya abrió: el fin solo se extiende, nunca se adelanta "
					+ "(adelantarlo adelantaría también el cierre).");
		}
		fechas(nuevoInicio, nuevoFin);
		actualizadoPor = Objects.requireNonNull(director, "director");
	}

	/** Abre el periodo (el sistema en su fecha o Dirección con su firma antes). */
	public void abrir(String quien, LocalDateTime ahora) {
		if (estado != EstadoPeriodo.PROGRAMADO) {
			throw new ReglaNegocioException("El " + nombre + " ya está " + estado.etiqueta().toLowerCase(Locale.ROOT) + ".");
		}
		estado = EstadoPeriodo.ABIERTO;
		abiertoPor = Objects.requireNonNull(quien, "quien");
		abiertoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** Dirección cierra el periodo con su firma (desde {@code fin − diasCierre}; lo valida el servicio y la base). */
	public void cerrar(String director, LocalDateTime ahora) {
		if (estado != EstadoPeriodo.ABIERTO) {
			throw new ReglaNegocioException("Solo se cierra un periodo abierto (el " + nombre + " está "
					+ estado.etiqueta().toLowerCase(Locale.ROOT) + ").");
		}
		if (director == null || director.startsWith("sistema")) {
			throw new IllegalStateException("Un periodo lo cierra una persona de Dirección, nunca el sistema");
		}
		estado = EstadoPeriodo.CERRADO;
		cerradoPor = director;
		cerradoEn = Objects.requireNonNull(ahora, "ahora");
	}

	/** El primer día desde el que Dirección puede cerrarlo (S9). */
	public LocalDate cierreDesde(int diasCierreAnticipado) {
		return fin.minusDays(diasCierreAnticipado);
	}

	private void fechas(LocalDate nuevoInicio, LocalDate nuevoFin) {
		if (nuevoInicio == null || nuevoFin == null) {
			throw new ReglaNegocioException("Escribe el inicio y el fin del periodo.");
		}
		if (!nuevoInicio.isBefore(nuevoFin)) {
			throw new ReglaNegocioException("El inicio del periodo va antes de su fin.");
		}
		inicio = nuevoInicio;
		fin = nuevoFin;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los periodos de evaluación no se borran.");
	}

	public Long getAnioEscolarId() {
		return anioEscolarId;
	}

	public int getNumero() {
		return numero;
	}

	public String getNombre() {
		return nombre;
	}

	public LocalDate getInicio() {
		return inicio;
	}

	public LocalDate getFin() {
		return fin;
	}

	public EstadoPeriodo getEstado() {
		return estado;
	}

	public String getAbiertoPor() {
		return abiertoPor;
	}

	public LocalDateTime getAbiertoEn() {
		return abiertoEn;
	}

	public String getCerradoPor() {
		return cerradoPor;
	}

	public LocalDateTime getCerradoEn() {
		return cerradoEn;
	}

	public String getActualizadoPor() {
		return actualizadoPor;
	}
}
