package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Orden de pago en línea: la crea el apoderado eligiendo cuotas de SU familia y el monto lo calcula el servidor (Σ de
 * los saldos al crearla, en {@link OrdenPagoCuota}). Nada se borra.
 * <ul>
 *   <li>Monto, familia, apoderado, referencia y vencimiento no cambian ({@code updatable = false} y GRANT por columna).</li>
 *   <li>El enlace con la pasarela se registra una vez; la confirmación (cargo, operación, monto, moneda, medio, fecha)
 *       también, con lo que respondió la CONSULTA a la pasarela, nunca con lo que dice un aviso.</li>
 *   <li>Las transiciones y «PAGADA exige su pago» las vigila además {@code trg_orden_pago_estado} en MySQL.</li>
 * </ul>
 */
@Entity
@Table(name = "orden_pago")
public class OrdenPago extends BaseEntity {

	public static final String MONEDA = "PEN";

	@Column(nullable = false, updatable = false, length = 36)
	private String referencia;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "familia_id", nullable = false, updatable = false)
	private Familia familia;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "apoderado_id", nullable = false, updatable = false)
	private Apoderado apoderado;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ProveedorPasarela proveedor;

	@Column(nullable = false, updatable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, updatable = false, length = 3)
	private String moneda;

	@Enumerated(EnumType.STRING)
	@Column(name = "comprobante_tipo", nullable = false, updatable = false, length = 20)
	private TipoComprobante comprobanteTipo;

	@Column(name = "clave_idempotencia", nullable = false, updatable = false, length = 36)
	private String claveIdempotencia;

	@Column(name = "vence_en", nullable = false, updatable = false)
	private LocalDateTime venceEn;

	// --- Lo que puede cambiar (GRANT UPDATE por columna; trg_orden_pago_estado vigila el resto) ---

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoOrden estado;

	@Column(name = "proveedor_orden_id", length = 80)
	private String proveedorOrdenId;

	@Column(name = "enlace_pago", length = 500)
	private String enlacePago;

	@Column(name = "cargo_id", length = 80)
	private String cargoId;

	@Column(length = 30)
	private String operacion;

	@Column(name = "monto_confirmado", precision = 10, scale = 2)
	private BigDecimal montoConfirmado;

	@Column(name = "moneda_confirmada", length = 3)
	private String monedaConfirmada;

	@Enumerated(EnumType.STRING)
	@Column(name = "medio_confirmado", length = 20)
	private MedioPago medioConfirmado;

	@Column(name = "confirmado_en")
	private LocalDateTime confirmadoEn;

	@Column(nullable = false)
	private boolean tardia;

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_revision", length = 30)
	private MotivoRevision motivoRevision;

	@Column(name = "detalle_revision", length = 500)
	private String detalleRevision;

	@Column(name = "devolucion_operacion", length = 80)
	private String devolucionOperacion;

	@Column(name = "devuelto_por", length = 60)
	private String devueltoPor;

	@Column(name = "devuelto_en")
	private LocalDateTime devueltoEn;

	/**
	 * Correcciones del sprint 4 (S4-A3): cuándo llegó el contracargo (el apoderado desconoció el cargo y su banco le
	 * devolvió el dinero) y por dónde (aviso o liquidación). Se registra una vez; desde entonces el ingreso no se aplica ni
	 * se devuelve, y el pago (si lo hay) se anula sin reembolso.
	 */
	@Column(name = "contracargo_en")
	private LocalDateTime contracargoEn;

	@Column(name = "contracargo_origen", length = 20)
	private String contracargoOrigen;

	protected OrdenPago() {
		// requerido por JPA
	}

	/**
	 * Orden nueva (CREADA, sin enlace ni confirmación). {@code monto} es la suma de los saldos de sus cuotas: lo calcula
	 * el servidor, nunca el formulario.
	 */
	public static OrdenPago crear(Familia familia, Apoderado apoderado, ProveedorPasarela proveedor, BigDecimal monto,
			TipoComprobante comprobante, UUID clave, LocalDateTime venceEn) {
		Objects.requireNonNull(familia, "familia");
		Objects.requireNonNull(apoderado, "apoderado");
		if (!Objects.equals(apoderado.getFamilia().getId(), familia.getId())) {
			throw new IllegalArgumentException("El apoderado de la orden es de esa familia");
		}
		if (comprobante != TipoComprobante.BOLETA && comprobante != TipoComprobante.FACTURA) {
			throw new IllegalArgumentException("Una orden emite boleta o factura");
		}
		OrdenPago orden = new OrdenPago();
		orden.referencia = UUID.randomUUID().toString();
		orden.familia = familia;
		orden.apoderado = apoderado;
		orden.proveedor = Objects.requireNonNull(proveedor, "proveedor");
		orden.monto = Dinero.positivo(monto, "el monto de la orden");
		orden.moneda = MONEDA;
		orden.comprobanteTipo = comprobante;
		orden.claveIdempotencia = Objects.requireNonNull(clave, "clave").toString();
		orden.venceEn = Objects.requireNonNull(venceEn, "venceEn");
		orden.estado = EstadoOrden.CREADA;
		return orden;
	}

	/** El enlace de la página de pago de la pasarela: una sola vez, con la orden CREADA. */
	public void registrarEnlace(String idEnPasarela, String url) {
		if (estado != EstadoOrden.CREADA || proveedorOrdenId != null) {
			throw new IllegalStateException("El enlace con la pasarela se registra una vez, con la orden en curso");
		}
		proveedorOrdenId = texto(idEnPasarela, 80, "el id de la orden en la pasarela");
		enlacePago = texto(url, 500, "el enlace de pago");
	}

	/** Lo que confirmó la CONSULTA a la pasarela. Una sola vez (en MySQL, el trigger rechaza reescribirla). */
	public void registrarConfirmacion(CobroConfirmado cobro) {
		Objects.requireNonNull(cobro, "cobro");
		if (cargoId != null) {
			if (!cargoId.equals(cobro.cargoId())) {
				throw new IllegalStateException("La orden ya tiene otra confirmación de la pasarela");
			}
			return;
		}
		if (!estado.admiteConfirmacion()) {
			throw new IllegalStateException("La orden " + estado + " ya no recibe confirmaciones");
		}
		cargoId = texto(cobro.cargoId(), 80, "el cargo");
		operacion = texto(cobro.operacionCanonica(), 30, "la operación");
		montoConfirmado = Dinero.positivo(cobro.monto(), "el monto confirmado");
		monedaConfirmada = texto(cobro.moneda(), 3, "la moneda");
		medioConfirmado = Objects.requireNonNull(cobro.medio(), "medio");
		confirmadoEn = Objects.requireNonNull(cobro.pagadoEn(), "pagadoEn");
	}

	/** La pasarela confirmó y el pago ya está registrado (en MySQL el trigger exige el pago VIGENTE). */
	public void marcarPagada() {
		exigirConfirmada();
		if (!estado.admiteConfirmacion()) {
			throw new IllegalStateException("La orden " + estado + " no pasa a pagada");
		}
		tardia = estado == EstadoOrden.VENCIDA;
		estado = EstadoOrden.PAGADA;
	}

	/** Hubo dinero pero no se pudo aplicar solo: Administración pide aplicarlo a otras cuotas o devolverlo. */
	public void marcarPorRevisar(MotivoRevision motivo, String detalle) {
		exigirConfirmada();
		if (!estado.admiteConfirmacion()) {
			throw new IllegalStateException("La orden " + estado + " no pasa a por revisar");
		}
		tardia = estado == EstadoOrden.VENCIDA;
		estado = EstadoOrden.POR_REVISAR;
		motivoRevision = Objects.requireNonNull(motivo, "motivo");
		detalleRevision = detalle == null ? null : recortar(detalle, 500);
	}

	/** Pasó su vigencia sin pagarse (la pasarela lo confirmó o nunca se llegó a crear en ella). */
	public void vencer() {
		if (estado != EstadoOrden.CREADA) {
			throw new IllegalStateException("Solo vence una orden en curso");
		}
		estado = EstadoOrden.VENCIDA;
	}

	/** La pasarela la rechazó (el emisor de la tarjeta o la billetera no autorizó el cobro). */
	public void rechazar() {
		if (estado != EstadoOrden.CREADA) {
			throw new IllegalStateException("Solo se rechaza una orden en curso");
		}
		estado = EstadoOrden.RECHAZADA;
	}

	/** El ingreso por revisar se aplicó con una aplicación aprobada (el pago ya está registrado). */
	public void marcarAplicada() {
		if (estado != EstadoOrden.POR_REVISAR) {
			throw new IllegalStateException("Solo se aplica una orden por revisar");
		}
		exigirSinContracargo();
		estado = EstadoOrden.APLICADA;
	}

	/** El ingreso por revisar se devolvió al mismo medio de origen, con la devolución aprobada por otra persona. */
	public void marcarDevuelta(String operacionReembolso, String por, LocalDateTime en) {
		if (estado != EstadoOrden.POR_REVISAR) {
			throw new IllegalStateException("Solo se devuelve una orden por revisar");
		}
		exigirSinContracargo();
		devolucionOperacion = texto(operacionReembolso, 80, "la operación del reembolso");
		devueltoPor = Objects.requireNonNull(por, "por");
		devueltoEn = Objects.requireNonNull(en, "en");
		estado = EstadoOrden.DEVUELTA;
	}

	/**
	 * Registra el contracargo una sola vez (por el aviso de la pasarela o por la línea de su liquidación).
	 *
	 * @return {@code false} si ya estaba registrado
	 */
	public boolean registrarContracargo(String origen, LocalDateTime en) {
		if (contracargoEn != null) {
			return false;
		}
		if (cargoId == null) {
			throw new IllegalStateException("Solo una orden cobrada por la pasarela recibe un contracargo");
		}
		if (!"AVISO".equals(origen) && !"LIQUIDACION".equals(origen)) {
			throw new IllegalArgumentException("Origen del contracargo no válido: " + origen);
		}
		contracargoOrigen = origen;
		contracargoEn = Objects.requireNonNull(en, "en");
		return true;
	}

	public boolean tieneContracargo() {
		return contracargoEn != null;
	}

	public LocalDateTime getContracargoEn() {
		return contracargoEn;
	}

	public String getContracargoOrigen() {
		return contracargoOrigen;
	}

	public boolean vencidaAl(LocalDateTime ahora) {
		return !ahora.isBefore(venceEn);
	}

	/** S4-A3: con contracargo, el dinero ya volvió al apoderado por su banco: no se aplica ni se devuelve otra vez. */
	public void exigirSinContracargo() {
		if (contracargoEn != null) {
			throw new ReglaNegocioException("Este pago en línea tuvo un contracargo: el apoderado ya recuperó su dinero por "
					+ "su banco. No se aplica a cuotas ni se devuelve otra vez.");
		}
	}

	private void exigirConfirmada() {
		if (cargoId == null) {
			throw new IllegalStateException("Primero se registra la confirmación de la pasarela");
		}
	}

	private static String texto(String valor, int maximo, String que) {
		if (valor == null || valor.isBlank() || valor.length() > maximo) {
			throw new ReglaNegocioException("La pasarela respondió sin " + que + " o con un valor no válido.");
		}
		return valor;
	}

	private static String recortar(String texto, int maximo) {
		String limpio = java.util.Objects.requireNonNullElse(Normalizador.limpiar(texto), "");
		return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo - 1) + "…";
	}

	@PreRemove
	void impedirBorrado() {
		throw new IllegalStateException("Las órdenes de pago no se borran.");
	}

	public String getReferencia() {
		return referencia;
	}

	public Familia getFamilia() {
		return familia;
	}

	public Apoderado getApoderado() {
		return apoderado;
	}

	public ProveedorPasarela getProveedor() {
		return proveedor;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public String getMoneda() {
		return moneda;
	}

	public TipoComprobante getComprobanteTipo() {
		return comprobanteTipo;
	}

	public String getClaveIdempotencia() {
		return claveIdempotencia;
	}

	public LocalDateTime getVenceEn() {
		return venceEn;
	}

	public EstadoOrden getEstado() {
		return estado;
	}

	public String getProveedorOrdenId() {
		return proveedorOrdenId;
	}

	public String getEnlacePago() {
		return enlacePago;
	}

	public String getCargoId() {
		return cargoId;
	}

	public String getOperacion() {
		return operacion;
	}

	public BigDecimal getMontoConfirmado() {
		return montoConfirmado;
	}

	public String getMonedaConfirmada() {
		return monedaConfirmada;
	}

	public MedioPago getMedioConfirmado() {
		return medioConfirmado;
	}

	public LocalDateTime getConfirmadoEn() {
		return confirmadoEn;
	}

	public boolean isTardia() {
		return tardia;
	}

	public MotivoRevision getMotivoRevision() {
		return motivoRevision;
	}

	public String getDetalleRevision() {
		return detalleRevision;
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
