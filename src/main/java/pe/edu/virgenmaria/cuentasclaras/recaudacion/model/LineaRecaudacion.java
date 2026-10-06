package pe.edu.virgenmaria.cuentasclaras.recaudacion.model;

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
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Una línea del archivo del banco: un pago hecho con el código del alumno (y, con base de deudas, la cuota exacta). Entra
 * PENDIENTE mientras el lote está CARGADO; el monto, la fecha, el código y la operación no cambian ({@code updatable =
 * false} y GRANT por columna). Luego solo cambia su estado al aplicarse (con su pago, uno por línea) o al quedar en
 * excepción con su motivo; una excepción se aplica o se devuelve solo con la aprobación de otra persona.
 */
@Entity
@Table(name = "linea_recaudacion")
public class LineaRecaudacion extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lote_id", nullable = false, updatable = false)
	private LoteRecaudacion lote;

	@Column(nullable = false, updatable = false)
	private int numero;

	@Column(name = "fecha_pago", nullable = false, updatable = false)
	private LocalDate fechaPago;

	@Column(nullable = false, updatable = false, length = 20)
	private String codigo;

	/** El alumno del código (si el código es válido y es de este colegio). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "alumno_id", updatable = false)
	private Alumno alumno;

	/** La cuota exacta, si el banco trabaja con base de deudas y la referencia es válida. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cuota_id", updatable = false)
	private Cuota cuota;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, updatable = false, length = 3)
	private String moneda;

	@Column(name = "numero_operacion", nullable = false, updatable = false, length = 30)
	private String numeroOperacion;

	// --- Lo que puede cambiar (GRANT UPDATE por columna; trg_linea_recaudacion_estado vigila las transiciones) ---

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoLinea estado;

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_excepcion", length = 30)
	private MotivoExcepcion motivoExcepcion;

	@Column(length = 250)
	private String detalle;

	@Column(name = "devolucion_operacion", length = 30)
	private String devolucionOperacion;

	@Column(name = "devuelto_por", length = 60)
	private String devueltoPor;

	@Column(name = "devuelto_en")
	private LocalDateTime devueltoEn;

	/** S4-A4 (correcciones del sprint 4): la cuenta a la que se devolvió (la aprobada en la solicitud). */
	@Column(name = "devolucion_banco", length = 20)
	private String devolucionBanco;

	@Column(name = "devolucion_cuenta", length = 30)
	private String devolucionCuenta;

	@Column(name = "devolucion_titular", length = 120)
	private String devolucionTitular;

	protected LineaRecaudacion() {
		// requerido por JPA
	}

	/**
	 * @param operacion número de operación del banco ya en su forma canónica ({@code NumeroOperacion.normalizar})
	 */
	public static LineaRecaudacion nueva(LoteRecaudacion lote, int numero, LocalDate fechaPago, String codigo,
			Alumno alumno, Cuota cuota, BigDecimal monto, String moneda, String operacion) {
		if (numero < 1) {
			throw new IllegalArgumentException("Las líneas se numeran desde 1");
		}
		LineaRecaudacion linea = new LineaRecaudacion();
		linea.lote = Objects.requireNonNull(lote, "lote");
		linea.numero = numero;
		linea.fechaPago = Objects.requireNonNull(fechaPago, "fechaPago");
		linea.codigo = Objects.requireNonNull(codigo, "codigo");
		linea.alumno = alumno;
		linea.cuota = cuota;
		linea.monto = Dinero.positivo(monto, "el monto de la línea");
		linea.moneda = Objects.requireNonNull(moneda, "moneda");
		linea.numeroOperacion = Objects.requireNonNull(operacion, "operacion");
		linea.estado = EstadoLinea.PENDIENTE;
		return linea;
	}

	/** Se registró su pago (en MySQL lo exige el trigger). */
	public void aplicar() {
		exigir(EstadoLinea.PENDIENTE);
		estado = EstadoLinea.APLICADA;
	}

	/** No se pudo aplicar sola: queda por revisar con su motivo (el motivo ya no cambia). */
	public void marcarExcepcion(MotivoExcepcion motivo, String detalle) {
		exigir(EstadoLinea.PENDIENTE);
		estado = EstadoLinea.EXCEPCION;
		motivoExcepcion = Objects.requireNonNull(motivo, "motivo");
		this.detalle = recortar(detalle);
	}

	/** Se registró su pago con una aplicación aprobada por otra persona. */
	public void aplicarTrasRevision() {
		exigir(EstadoLinea.EXCEPCION);
		estado = EstadoLinea.APLICADA_REVISION;
	}

	/** Se devolvió con la transferencia {@code operacion}, aprobada por otra persona que no es {@code por}. */
	public void marcarDevuelta(String operacion, String banco, String cuenta, String titular, String por,
			LocalDateTime ahora) {
		exigir(EstadoLinea.EXCEPCION);
		estado = EstadoLinea.DEVUELTA;
		devolucionOperacion = Objects.requireNonNull(operacion, "operacion");
		devolucionBanco = Objects.requireNonNull(banco, "banco");
		devolucionCuenta = Objects.requireNonNull(cuenta, "cuenta");
		devolucionTitular = Objects.requireNonNull(titular, "titular");
		devueltoPor = Objects.requireNonNull(por, "por");
		devueltoEn = Objects.requireNonNull(ahora, "ahora");
	}

	public String getDevolucionBanco() {
		return devolucionBanco;
	}

	public String getDevolucionCuenta() {
		return devolucionCuenta;
	}

	public String getDevolucionTitular() {
		return devolucionTitular;
	}

	private void exigir(EstadoLinea esperado) {
		if (estado != esperado) {
			throw new IllegalStateException("La línea " + numero + " está " + estado + ", no " + esperado);
		}
	}

	private static String recortar(String texto) {
		if (texto == null) {
			return null;
		}
		return texto.length() <= 250 ? texto : texto.substring(0, 249) + "…";
	}

	public boolean enPesos() {
		return Dinero.MONEDA.equals(moneda);
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las líneas de recaudación no se borran: se aplican, se revisan o se devuelven.");
	}

	public LoteRecaudacion getLote() {
		return lote;
	}

	public int getNumero() {
		return numero;
	}

	public LocalDate getFechaPago() {
		return fechaPago;
	}

	public String getCodigo() {
		return codigo;
	}

	public Alumno getAlumno() {
		return alumno;
	}

	public Cuota getCuota() {
		return cuota;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public String getMoneda() {
		return moneda;
	}

	public String getNumeroOperacion() {
		return numeroOperacion;
	}

	public EstadoLinea getEstado() {
		return estado;
	}

	public MotivoExcepcion getMotivoExcepcion() {
		return motivoExcepcion;
	}

	public String getDetalle() {
		return detalle;
	}

	public String getDevolucionOperacion() {
		return devolucionOperacion;
	}

	public String getDevueltoPor() {
		return devueltoPor;
	}

	public LocalDateTime getDevueltoEn() {
		return devueltoEn;
	}
}
