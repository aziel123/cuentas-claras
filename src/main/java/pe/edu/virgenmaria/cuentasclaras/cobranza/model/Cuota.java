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
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Obligación de pago de un alumno: la genera el sistema (MATRICULA, PENSION) o nace de un saldo inicial confirmado.
 * <ul>
 *   <li>Nunca se borra ({@code @PreRemove} y, en MySQL, sin DELETE).</li>
 *   <li>Monto, vencimiento, alumno, origen y clave son inmutables: {@code updatable = false}, sin setters y, en
 *       MySQL, un GRANT de UPDATE solo por columna. Las columnas que aquí se pueden actualizar deben coincidir
 *       EXACTAMENTE con ese GRANT (lo comprueba {@code InmutabilidadCuotasTest}).</li>
 *   <li>VENCIDA no se guarda: {@link #estadoAl(LocalDate)} la calcula con la fecha de Lima.</li>
 * </ul>
 */
@Entity
@Table(name = "cuota")
public class Cuota extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alumno_id", nullable = false, updatable = false)
	private Alumno alumno;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "anio_escolar_id", nullable = false, updatable = false)
	private AnioEscolar anioEscolar;

	@Column(name = "matricula_id", updatable = false)
	private Long matriculaId;

	@Column(name = "plan_pension_id", updatable = false)
	private Long planPensionId;

	@Column(name = "linea_saldo_inicial_id", updatable = false)
	private Long lineaSaldoInicialId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private TipoCuota tipo;

	@Column(updatable = false)
	private Integer numero;

	@Column(nullable = false, updatable = false, length = 80)
	private String descripcion;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(name = "fecha_vencimiento", nullable = false, updatable = false)
	private LocalDate fechaVencimiento;

	@Column(nullable = false, updatable = false, length = 80)
	private String clave;

	// --- Lo único que puede cambiar (GRANT UPDATE por columna en MySQL) ---

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoCuota estado;

	@Column(name = "monto_pagado", nullable = false, precision = 10, scale = 2)
	private BigDecimal montoPagado;

	@Column(length = 20)
	private String obligacion;

	@Column(name = "anulacion_motivo", length = 500)
	private String anulacionMotivo;

	@Column(name = "anulacion_solicitada_por", length = 60)
	private String anulacionSolicitadaPor;

	@Column(name = "anulacion_aprobada_por", length = 60)
	private String anulacionAprobadaPor;

	@Column(name = "anulada_en")
	private LocalDateTime anuladaEn;

	protected Cuota() {
		// requerido por JPA
	}

	/** Cuota de matrícula o de pensión que manda el plan aprobado. */
	public static Cuota generada(Matricula matricula, PlanPension plan, CuotaPlanificada planificada) {
		Objects.requireNonNull(matricula, "matricula");
		Objects.requireNonNull(plan, "plan");
		if (!plan.aprobado()) {
			throw new ReglaNegocioException("Solo un plan aprobado genera cuotas.");
		}
		if (planificada.tipo() == TipoCuota.SALDO_INICIAL) {
			throw new IllegalArgumentException("El saldo inicial no sale de un plan");
		}
		Cuota cuota = nueva(matricula.getAlumno(), matricula.getAnioEscolar(), planificada.tipo(), planificada.numero(),
				planificada.descripcion(), planificada.monto(), planificada.vencimiento(), planificada.clave(),
				planificada.obligacion());
		cuota.matriculaId = matricula.getId();
		cuota.planPensionId = plan.getId();
		return cuota;
	}

	/** Cuota que nace de una línea de un lote de saldo inicial confirmado. La matrícula es opcional. */
	public static Cuota deSaldoInicial(LineaSaldoInicial linea, Matricula matricula) {
		Objects.requireNonNull(linea.getId(), "la línea debe estar guardada");
		Cuota cuota = nueva(linea.getAlumno(), linea.getLote().getAnioEscolar(), TipoCuota.SALDO_INICIAL, null,
				linea.getDescripcion(), linea.getMonto(), linea.getFechaVencimiento(), linea.clave(), linea.obligacion());
		cuota.lineaSaldoInicialId = linea.getId();
		cuota.matriculaId = matricula == null ? null : matricula.getId();
		return cuota;
	}

	private static Cuota nueva(Alumno alumno, AnioEscolar anio, TipoCuota tipo, Integer numero, String descripcion,
			BigDecimal monto, LocalDate vencimiento, String clave, String obligacion) {
		Cuota cuota = new Cuota();
		cuota.alumno = Objects.requireNonNull(alumno, "alumno");
		cuota.anioEscolar = Objects.requireNonNull(anio, "anio");
		cuota.tipo = tipo;
		cuota.numero = numero;
		cuota.descripcion = Objects.requireNonNull(descripcion, "descripcion");
		cuota.monto = Dinero.positivo(monto, "el monto de la cuota");
		cuota.fechaVencimiento = Objects.requireNonNull(vencimiento, "vencimiento");
		cuota.clave = Objects.requireNonNull(clave, "clave");
		cuota.obligacion = obligacion;
		cuota.estado = EstadoCuota.PENDIENTE;
		cuota.montoPagado = Dinero.CERO;
		return cuota;
	}

	/** Estado que ve el usuario. Vencida si {@code hoy > vencimiento} y aún debe algo: el día del vencimiento vale. */
	public EstadoVisibleCuota estadoAl(LocalDate hoy) {
		return switch (estado) {
			case ANULADA -> EstadoVisibleCuota.ANULADA;
			case PAGADA -> EstadoVisibleCuota.PAGADA;
			case PARCIAL -> vencidaAl(hoy) ? EstadoVisibleCuota.VENCIDA : EstadoVisibleCuota.PARCIAL;
			case PENDIENTE -> vencidaAl(hoy) ? EstadoVisibleCuota.VENCIDA : EstadoVisibleCuota.PENDIENTE;
		};
	}

	public boolean vencidaAl(LocalDate hoy) {
		return estado != EstadoCuota.ANULADA && estado != EstadoCuota.PAGADA && hoy.isAfter(fechaVencimiento);
	}

	/** Lo que falta pagar; una anulada no suma. */
	public BigDecimal saldo() {
		return estado == EstadoCuota.ANULADA ? Dinero.CERO : monto.subtract(montoPagado);
	}

	public boolean anulada() {
		return estado == EstadoCuota.ANULADA;
	}

	/** Hay una solicitud de anulación esperando aprobación (sprint 3). */
	public boolean anulacionPendiente() {
		return estado != EstadoCuota.ANULADA && anulacionSolicitadaPor != null;
	}

	/**
	 * Deja una solicitud de anulación pendiente: la cuota sigue vigente y se sigue debiendo. La aprueba otra persona
	 * en el sprint 3 (Aprobaciones), con {@link #anular}.
	 */
	public void solicitarAnulacion(String motivo, String solicitante) {
		exigirAnulable();
		if (anulacionPendiente()) {
			throw new ReglaNegocioException("La cuota ya tiene una solicitud de anulación pendiente.");
		}
		anulacionMotivo = Motivo.exigir(motivo);
		anulacionSolicitadaPor = Objects.requireNonNull(solicitante, "solicitante");
	}

	/**
	 * Gancho para el sprint 3: anula con el motivo y la aprobación de alguien distinto de quien la solicitó (también
	 * es CHECK en la base). Libera la obligación (podrá generarse otra cuota del mismo mes) y conserva la clave.
	 */
	public void anular(String motivo, String solicitante, String aprobador, LocalDateTime ahora) {
		exigirAnulable();
		String texto = Motivo.exigir(motivo);
		Objects.requireNonNull(solicitante, "solicitante");
		if (aprobador == null || aprobador.equals(solicitante)) {
			throw new AutoaprobacionException("Quien solicita la anulación no puede aprobarla.");
		}
		estado = EstadoCuota.ANULADA;
		obligacion = null;
		anulacionMotivo = texto;
		anulacionSolicitadaPor = solicitante;
		anulacionAprobadaPor = aprobador;
		anuladaEn = Objects.requireNonNull(ahora, "ahora");
	}

	private void exigirAnulable() {
		if (estado == EstadoCuota.ANULADA) {
			throw new ReglaNegocioException("La cuota ya está anulada.");
		}
		if (montoPagado.signum() > 0) {
			throw new ReglaNegocioException("La cuota tiene pagos: primero se anulan los pagos.");
		}
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las cuotas no se borran: se anulan con motivo y aprobación.");
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public AnioEscolar getAnioEscolar() {
		return anioEscolar;
	}

	public Long getMatriculaId() {
		return matriculaId;
	}

	public Long getPlanPensionId() {
		return planPensionId;
	}

	public Long getLineaSaldoInicialId() {
		return lineaSaldoInicialId;
	}

	public TipoCuota getTipo() {
		return tipo;
	}

	public Integer getNumero() {
		return numero;
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

	public String getClave() {
		return clave;
	}

	public EstadoCuota getEstado() {
		return estado;
	}

	public BigDecimal getMontoPagado() {
		return montoPagado;
	}

	public String getObligacion() {
		return obligacion;
	}

	public String getAnulacionMotivo() {
		return anulacionMotivo;
	}

	public String getAnulacionSolicitadaPor() {
		return anulacionSolicitadaPor;
	}

	public String getAnulacionAprobadaPor() {
		return anulacionAprobadaPor;
	}

	public LocalDateTime getAnuladaEn() {
		return anuladaEn;
	}
}
