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
 *   <li>Lo pagado y lo descontado son libros (sprint 3): {@code monto_pagado} es la suma de {@code aplicacion_pago} y
 *       {@code monto_descuento} la de {@code ajuste_cuota}. Solo cambian por {@link #reflejarPagos} y
 *       {@link #reflejarDescuentos} (ArchUnit limita quién los llama) y, en MySQL, un trigger rechaza cualquier otro
 *       valor: no puede existir una cuota PAGADA sin pago.</li>
 *   <li>Saldo = monto − descuento − pagado. EXONERADA: descuento del 100 % (saldo 0 sin pagos).</li>
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

	@Column(name = "monto_descuento", nullable = false, precision = 10, scale = 2)
	private BigDecimal montoDescuento;

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
		// Hibernate inserta el valor del campo, no el DEFAULT de la base: sin esto la columna NOT NULL falla.
		cuota.montoDescuento = Dinero.CERO;
		return cuota;
	}

	/** Estado que ve el usuario. Vencida si {@code hoy > vencimiento} y aún debe algo: el día del vencimiento vale. */
	public EstadoVisibleCuota estadoAl(LocalDate hoy) {
		return switch (estado) {
			case ANULADA -> EstadoVisibleCuota.ANULADA;
			case PAGADA -> EstadoVisibleCuota.PAGADA;
			case EXONERADA -> EstadoVisibleCuota.EXONERADA;
			case PARCIAL -> vencidaAl(hoy) ? EstadoVisibleCuota.VENCIDA : EstadoVisibleCuota.PARCIAL;
			case PENDIENTE -> vencidaAl(hoy) ? EstadoVisibleCuota.VENCIDA : EstadoVisibleCuota.PENDIENTE;
		};
	}

	public boolean vencidaAl(LocalDate hoy) {
		return (estado == EstadoCuota.PENDIENTE || estado == EstadoCuota.PARCIAL) && hoy.isAfter(fechaVencimiento);
	}

	/** Lo que falta pagar: monto − descuento − pagado. Una anulada no suma. */
	public BigDecimal saldo() {
		return estado == EstadoCuota.ANULADA ? Dinero.CERO : monto.subtract(montoDescuento).subtract(montoPagado);
	}

	/** Se puede cobrar: PENDIENTE o PARCIAL y sin una anulación esperando aprobación. */
	public boolean admiteCobro() {
		return (estado == EstadoCuota.PENDIENTE || estado == EstadoCuota.PARCIAL) && !anulacionPendiente();
	}

	/**
	 * Refleja el libro de pagos: {@code totalLibro} es la suma de las aplicaciones de esta cuota (la calcula la base).
	 * Recalcula el estado. Lo llama solo {@code caja.service} (regla ArchUnit); en MySQL un trigger exige que el valor
	 * sea esa suma.
	 *
	 * @throws ReglaNegocioException si la cuota está anulada o lo pagado supera lo que se debe
	 */
	public void reflejarPagos(BigDecimal totalLibro) {
		BigDecimal pagado = Dinero.normalizar(totalLibro);
		exigirLibro(pagado, montoDescuento, "Lo pagado");
		montoPagado = pagado;
		estado = estadoSegun(montoPagado, montoDescuento);
	}

	/**
	 * Refleja el libro de descuentos: {@code totalAjustes} es la suma de los ajustes aprobados de esta cuota. Lo llama
	 * solo el manejador de descuentos (regla ArchUnit); en MySQL un trigger exige que el valor sea esa suma.
	 */
	public void reflejarDescuentos(BigDecimal totalAjustes) {
		BigDecimal descuento = Dinero.normalizar(totalAjustes);
		exigirLibro(montoPagado, descuento, "El descuento");
		montoDescuento = descuento;
		estado = estadoSegun(montoPagado, montoDescuento);
	}

	private void exigirLibro(BigDecimal pagado, BigDecimal descuento, String que) {
		if (estado == EstadoCuota.ANULADA) {
			throw new ReglaNegocioException("La cuota " + descripcion + " está anulada: no recibe pagos ni descuentos.");
		}
		if (pagado.signum() < 0 || descuento.signum() < 0) {
			throw new ReglaNegocioException(que + " de la cuota " + descripcion + " no puede ser negativo.");
		}
		if (pagado.add(descuento).compareTo(monto) > 0) {
			throw new ReglaNegocioException(que + " de la cuota " + descripcion + " supera lo que se debe ("
					+ Dinero.formatear(monto) + ").");
		}
	}

	private EstadoCuota estadoSegun(BigDecimal pagado, BigDecimal descuento) {
		if (pagado.signum() == 0) {
			return descuento.compareTo(monto) == 0 ? EstadoCuota.EXONERADA : EstadoCuota.PENDIENTE;
		}
		return pagado.add(descuento).compareTo(monto) == 0 ? EstadoCuota.PAGADA : EstadoCuota.PARCIAL;
	}

	public boolean anulada() {
		return estado == EstadoCuota.ANULADA;
	}

	/** Hay una solicitud de anulación esperando aprobación (en la bandeja de Aprobaciones). */
	public boolean anulacionPendiente() {
		return estado != EstadoCuota.ANULADA && anulacionSolicitadaPor != null;
	}

	/**
	 * Deja una solicitud de anulación pendiente: la cuota sigue vigente y se sigue debiendo. La aprueba otra persona
	 * en Aprobaciones, con {@link #anular}.
	 */
	public void solicitarAnulacion(String motivo, String solicitante) {
		exigirAnulable();
		if (anulacionPendiente()) {
			throw new ReglaNegocioException("La cuota ya tiene una solicitud de anulación pendiente.");
		}
		anulacionMotivo = Motivo.exigir(motivo);
		anulacionSolicitadaPor = Objects.requireNonNull(solicitante, "solicitante");
	}

	/** Se rechazó la solicitud de anulación: la cuota sigue igual (el motivo queda en la solicitud y la bitácora). */
	public void descartarSolicitudAnulacion() {
		if (!anulacionPendiente()) {
			throw new ReglaNegocioException("La cuota no tiene una solicitud de anulación pendiente.");
		}
		anulacionMotivo = null;
		anulacionSolicitadaPor = null;
	}

	/**
	 * Anula con el motivo y la aprobación de alguien distinto de quien la solicitó (también
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

	public BigDecimal getMontoDescuento() {
		return montoDescuento;
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
