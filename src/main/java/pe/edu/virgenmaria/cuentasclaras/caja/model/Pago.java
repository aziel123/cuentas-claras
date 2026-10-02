package pe.edu.virgenmaria.cuentasclaras.caja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Dinero recibido de UNA familia en UNA caja (la de su cajero en ese día). Libro de solo inserción:
 * <ul>
 *   <li>familia, caja, medio, operación, total, vuelto y comprobante no cambian ({@code updatable = false}, sin
 *       setters y, en MySQL, GRANT de UPDATE solo sobre {@code estado} y {@code operacion_vigente});</li>
 *   <li>nunca se borra: se anula con aprobación de otra persona (tanda 2);</li>
 *   <li>nace con su boleta o factura por el mismo total (en MySQL lo exige un trigger).</li>
 * </ul>
 * El total lo calcula el sistema con los saldos de las cuotas: la cajera nunca lo escribe.
 */
@Entity
@Table(name = "pago")
public class Pago extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "familia_id", nullable = false, updatable = false)
	private Familia familia;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "caja_diaria_id", nullable = false, updatable = false)
	private CajaDiaria caja;

	@Column(nullable = false, updatable = false, length = 60)
	private String cajero;

	@Column(nullable = false, updatable = false)
	private LocalDate fecha;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comprobante_id", nullable = false, updatable = false)
	private Comprobante comprobante;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private MedioPago medio;

	@Column(name = "numero_operacion", updatable = false, length = 30)
	private String numeroOperacion;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal total;

	@Column(updatable = false, precision = 10, scale = 2)
	private BigDecimal recibido;

	@Column(updatable = false, precision = 10, scale = 2)
	private BigDecimal vuelto;

	@Column(name = "a_cuenta", nullable = false, updatable = false)
	private boolean aCuenta;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private OrigenPago origen;

	@Column(name = "reemplaza_pago_id", updatable = false)
	private Long reemplazaPagoId;

	@Column(name = "clave_idempotencia", nullable = false, updatable = false, length = 36)
	private String claveIdempotencia;

	// --- Lo único que puede cambiar: la anulación (GRANT UPDATE por columna en MySQL) ---

	@Column(name = "operacion_vigente", length = 30)
	private String operacionVigente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoPago estado;

	protected Pago() {
		// requerido por JPA
	}

	/**
	 * Pago registrado por la cajera en su caja. {@code total} ya lo calculó el sistema; en efectivo, {@code recibido}
	 * es lo único que escribe la cajera (para el vuelto).
	 *
	 * @param operacion número de operación ya normalizado (obligatorio si es digital, null en efectivo)
	 */
	public static Pago enCaja(CajaDiaria caja, Familia familia, Comprobante comprobante, MedioPago medio,
			String operacion, BigDecimal total, BigDecimal recibido, boolean aCuenta, UUID clave) {
		Objects.requireNonNull(caja, "caja");
		Objects.requireNonNull(medio, "medio");
		Objects.requireNonNull(clave, "clave");
		Pago pago = new Pago();
		pago.familia = Objects.requireNonNull(familia, "familia");
		pago.caja = caja;
		pago.cajero = caja.getCajero();
		pago.fecha = caja.getFecha();
		pago.comprobante = Objects.requireNonNull(comprobante, "comprobante");
		pago.medio = medio;
		pago.total = Dinero.positivo(total, "el total del pago");
		if (comprobante.getTipo() == TipoComprobante.NOTA_CREDITO || !Dinero.iguales(comprobante.getTotal(), pago.total)) {
			throw new IllegalStateException("El pago necesita su boleta o factura por el mismo total");
		}
		if (medio.digital()) {
			if (operacion == null) {
				throw new ReglaNegocioException("El número de operación es obligatorio en un pago " + medio.etiqueta() + ".");
			}
			pago.numeroOperacion = operacion;
			pago.operacionVigente = operacion;
		}
		else {
			if (!caja.aceptaEfectivo()) {
				throw new ReglaNegocioException("La caja de hoy ya se cerró: solo se aceptan pagos digitales.");
			}
			pago.recibido = Dinero.normalizar(recibido);
			pago.vuelto = ReglasEfectivo.vuelto(pago.total, pago.recibido);
		}
		pago.aCuenta = aCuenta;
		pago.origen = OrigenPago.CAJA;
		pago.claveIdempotencia = clave.toString();
		pago.estado = EstadoPago.VIGENTE;
		return pago;
	}

	public boolean vigente() {
		return estado == EstadoPago.VIGENTE;
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Los pagos no se borran: se anulan con motivo y aprobación de otra persona.");
	}

	public Familia getFamilia() {
		return familia;
	}

	public CajaDiaria getCaja() {
		return caja;
	}

	public String getCajero() {
		return cajero;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public Comprobante getComprobante() {
		return comprobante;
	}

	public MedioPago getMedio() {
		return medio;
	}

	public String getNumeroOperacion() {
		return numeroOperacion;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public BigDecimal getRecibido() {
		return recibido;
	}

	public BigDecimal getVuelto() {
		return vuelto;
	}

	public boolean isACuenta() {
		return aCuenta;
	}

	public OrigenPago getOrigen() {
		return origen;
	}

	public Long getReemplazaPagoId() {
		return reemplazaPagoId;
	}

	public String getClaveIdempotencia() {
		return claveIdempotencia;
	}

	public String getOperacionVigente() {
		return operacionVigente;
	}

	public EstadoPago getEstado() {
		return estado;
	}
}
