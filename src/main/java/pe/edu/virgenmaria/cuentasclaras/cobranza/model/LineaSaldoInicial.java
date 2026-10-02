package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Una deuda previa al sistema de un alumno, dentro de un lote. No se borra ni se edita: se marca como quitada (y se
 * agrega otra si hacía falta corregirla).
 */
@Entity
@Table(name = "linea_saldo_inicial")
public class LineaSaldoInicial extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lote_id", nullable = false, updatable = false)
	private LoteSaldoInicial lote;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alumno_id", nullable = false, updatable = false)
	private Alumno alumno;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ConceptoSaldo concepto;

	/** Año real de la deuda (PENSION y MATRICULA). La obligación se calcula con él, nunca con el año del lote. */
	@Column(name = "anio_deuda", updatable = false)
	private Integer anioDeuda;

	@Column(updatable = false)
	private Integer mes;

	@Column(nullable = false, updatable = false, length = 80)
	private String descripcion;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(name = "fecha_vencimiento", nullable = false, updatable = false)
	private LocalDate fechaVencimiento;

	@Column(nullable = false)
	private boolean quitada;

	protected LineaSaldoInicial() {
		// requerido por JPA
	}

	static LineaSaldoInicial nueva(LoteSaldoInicial lote, Alumno alumno, ConceptoSaldo concepto, Integer anioDeuda,
			Integer mes, String descripcion, BigDecimal monto, LocalDate vencimiento) {
		LineaSaldoInicial linea = new LineaSaldoInicial();
		linea.lote = Objects.requireNonNull(lote, "lote");
		linea.alumno = Objects.requireNonNull(alumno, "alumno");
		linea.concepto = Objects.requireNonNull(concepto, "concepto");
		linea.anioDeuda = concepto == ConceptoSaldo.OTRO ? null : Objects.requireNonNull(anioDeuda, "anioDeuda");
		linea.mes = mes;
		linea.descripcion = Objects.requireNonNull(descripcion, "descripcion");
		linea.monto = Objects.requireNonNull(monto, "monto");
		linea.fechaVencimiento = Objects.requireNonNull(vencimiento, "vencimiento");
		return linea;
	}

	/**
	 * La deuda que representa, con su año REAL: «PEN-2026-05», «MAT-2026» u «OT-2026-1a2b3c4d» (otro concepto del
	 * reglamento en el año del lote). Es única por alumno: la misma deuda no se cobra dos veces.
	 */
	public String obligacion() {
		return switch (concepto) {
			case MATRICULA -> Obligaciones.matricula(anioDeuda);
			case PENSION -> Obligaciones.pension(anioDeuda, mes);
			case OTRO -> Obligaciones.otro(lote.getAnioEscolar().getAnio(), descripcion);
		};
	}

	/** Idempotencia de la cuota que crea al confirmarse el lote. */
	public String clave() {
		return "SI:" + getId();
	}

	void quitar() {
		quitada = true;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las líneas de saldo inicial no se borran: se marcan como quitadas.");
	}

	public LoteSaldoInicial getLote() {
		return lote;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public ConceptoSaldo getConcepto() {
		return concepto;
	}

	public Integer getAnioDeuda() {
		return anioDeuda;
	}

	public Integer getMes() {
		return mes;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public LocalDate getFechaVencimiento() {
		return fechaVencimiento;
	}

	public boolean isQuitada() {
		return quitada;
	}
}
