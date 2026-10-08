package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.AperturaSerie;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Única entrada de los canales automáticos (pagos en línea y recaudación bancaria) al libro de pagos (sprint 4). La usa
 * solo un actor de sistema ({@code sistema.pasarela} o {@code sistema.recaudacion}), dentro de la transacción de quien
 * llama, con este orden de bloqueos: (orden de pago o lote) → caja del canal → cuotas por id ascendente → serie →
 * bitácora.
 * <p>
 * {@link #evaluar} responde, SIN escribir, si el dinero se puede aplicar (cuotas cobrables de esa familia, saldo que no
 * cambió, operación no usada): así quien llama deja la orden «por revisar» en vez de romper la transacción y el dinero
 * no se aplica dos veces.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
@PreAuthorize("hasAnyRole('SISTEMA_PASARELA','SISTEMA_RECAUDACION')")
public class RegistroPagosAutomaticos {

	/** Por qué un ingreso no se puede aplicar solo (la orden queda POR_REVISAR). */
	public enum MotivoNoAplicable {
		CUOTA_NO_COBRABLE, MONTO_CAMBIO, EXCESO, OPERACION_DUPLICADA
	}

	/**
	 * Un pago en línea confirmado por la pasarela. {@code saldosEsperados}: el saldo de cada cuota al crear la orden (si
	 * alguno cambió, no se aplica). {@code aCuenta}: solo al aplicar un ingreso por revisar que no cubre todo.
	 */
	public record PedidoPagoEnLinea(Long familiaId, List<Long> cuotaIds, MedioPago medio, String operacion,
			BigDecimal monto, boolean aCuenta, DatosComprobante comprobante, Long ordenPagoId, UUID clave,
			LocalDate fechaCanal, LocalDate fechaComprobante, String detalle) {
	}

	/**
	 * Un pago hecho en el banco con el código del alumno (una línea del archivo de recaudación ya confirmado a ciegas).
	 * {@code fechaPago}: la del banco (la del pago y de su caja de canal); {@code fechaComprobante}: hoy (Lima).
	 * {@code aCuenta}: el monto no cubre todas sus cuotas (pago parcial, se resalta).
	 */
	public record PedidoRecaudacion(Long familiaId, String operacion, BigDecimal monto, boolean aCuenta, Long lineaId,
			UUID clave, LocalDate fechaPago, LocalDate fechaComprobante, String detalle) {
	}

	private final CajaDiariaRepository cajas;

	private final CuotaRepository cuotas;

	private final PagoRepository pagos;

	private final FamiliaRepository familias;

	private final LibroPagos libro;

	private final AperturaCaja aperturaCaja;

	private final AperturaSerie aperturaSerie;

	public RegistroPagosAutomaticos(CajaDiariaRepository cajas, CuotaRepository cuotas, PagoRepository pagos,
			FamiliaRepository familias, LibroPagos libro, AperturaCaja aperturaCaja, AperturaSerie aperturaSerie) {
		this.cajas = cajas;
		this.cuotas = cuotas;
		this.pagos = pagos;
		this.familias = familias;
		this.libro = libro;
		this.aperturaCaja = aperturaCaja;
		this.aperturaSerie = aperturaSerie;
	}

	/**
	 * ANTES de la transacción del pago (cada una en la suya): la caja del canal de ese día y las series de boleta y
	 * factura. Si otro proceso las crea a la vez, se usan esas.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void prepararCanal(CanalCaja canal, LocalDate fecha) {
		try {
			aperturaCaja.asegurarCanal(canal, fecha);
		}
		catch (DataIntegrityViolationException otraLaAbrio) {
			// La abrió a la vez otro proceso: se usa esa.
		}
		for (TipoComprobante tipo : List.of(TipoComprobante.BOLETA, TipoComprobante.FACTURA)) {
			try {
				aperturaSerie.crearSiFalta(tipo);
			}
			catch (DataIntegrityViolationException otroLaCreo) {
				// La creó a la vez otro cobro: se usa esa.
			}
		}
	}

	/** La caja del canal de ese día, bloqueada (ya abierta con {@link #prepararCanal}). */
	public CajaDiaria bloquearCanal(CanalCaja canal, LocalDate fecha) {
		CajaDiaria caja = cajas.findByCanalAndFecha(canal, fecha)
				.orElseThrow(() -> new IllegalStateException("La caja del canal " + canal + " del " + fecha + " no se abrió"));
		return cajas.bloquearPorId(caja.getId()).orElseThrow();
	}

	/** Las cuotas, bloqueadas en orden de id (si falta alguna, 404). */
	public List<Cuota> bloquearCuotas(Collection<Long> ids) {
		List<Long> orden = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
		List<Cuota> bloqueadas = cuotas.bloquear(orden);
		if (orden.isEmpty() || bloqueadas.size() != orden.size()) {
			throw new RecursoNoEncontradoException("Cuota no encontrada");
		}
		return bloqueadas;
	}

	/**
	 * Sin escribir nada, con las cuotas YA bloqueadas: si el dinero no se puede aplicar, el motivo.
	 *
	 * @param saldosEsperados saldo de cada cuota al crear la orden (exacto: deben seguir iguales); {@code null} para
	 *                        aplicar «hasta» el saldo (un ingreso por revisar que se aplica a otras cuotas)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<MotivoNoAplicable> evaluar(Long familiaId, List<Cuota> bloqueadas, BigDecimal monto,
			String operacion, Map<Long, BigDecimal> saldosEsperados) {
		for (Cuota cuota : bloqueadas) {
			if (!Objects.equals(cuota.getAlumno().getFamilia().getId(), familiaId) || !cuota.admiteCobro()
					|| cuota.anulacionPendiente() || cuota.saldo().signum() <= 0) {
				return Optional.of(MotivoNoAplicable.CUOTA_NO_COBRABLE);
			}
		}
		BigDecimal debe = Dinero.sumar(bloqueadas.stream().map(Cuota::saldo).toList());
		if (saldosEsperados != null) {
			for (Cuota cuota : bloqueadas) {
				BigDecimal esperado = saldosEsperados.get(cuota.getId());
				if (esperado == null || cuota.saldo().compareTo(esperado) != 0) {
					return Optional.of(MotivoNoAplicable.MONTO_CAMBIO);
				}
			}
			if (debe.compareTo(monto) != 0) {
				return Optional.of(MotivoNoAplicable.MONTO_CAMBIO);
			}
		}
		else if (monto.compareTo(debe) > 0) {
			return Optional.of(MotivoNoAplicable.EXCESO);
		}
		if (operacion != null && pagos.existsByOperacionVigente(operacion)) {
			return Optional.of(MotivoNoAplicable.OPERACION_DUPLICADA);
		}
		return Optional.empty();
	}

	/**
	 * Registra el pago en línea con su comprobante, sus aplicaciones y la bitácora, en la caja del canal YA bloqueada y
	 * con las cuotas YA bloqueadas y evaluadas.
	 */
	public Pago registrarEnLinea(CajaDiaria canal, List<Cuota> bloqueadas, PedidoPagoEnLinea pedido) {
		Objects.requireNonNull(pedido, "pedido");
		if (canal.getCanal() != CanalCaja.PASARELA || !canal.getFecha().equals(pedido.fechaCanal())) {
			throw new IllegalArgumentException("El pago en línea va a la caja PASARELA de su fecha");
		}
		Familia familia = familias.findById(pedido.familiaId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Familia no encontrada"));
		return libro.registrarEnCanal(canal, familia, bloqueadas, pedido.medio(), pedido.operacion(), pedido.monto(),
				pedido.aCuenta(), pedido.comprobante(), pedido.ordenPagoId(), pedido.clave(), pedido.fechaComprobante(),
				pedido.detalle());
	}

	/**
	 * Registra el pago de una línea de recaudación con su boleta (a nombre del responsable de pago del alumno), sus
	 * aplicaciones y la bitácora, en la caja RECAUDACION de la fecha de pago YA bloqueada y con las cuotas YA bloqueadas y
	 * evaluadas (solo las que reciben dinero, de la que vence primero a la última).
	 */
	public Pago registrarRecaudacion(CajaDiaria canal, List<Cuota> bloqueadas, PedidoRecaudacion pedido) {
		Objects.requireNonNull(pedido, "pedido");
		if (canal.getCanal() != CanalCaja.RECAUDACION || !canal.getFecha().equals(pedido.fechaPago())) {
			throw new IllegalArgumentException("El pago por banco va a la caja RECAUDACION de su fecha de pago");
		}
		Familia familia = familias.findById(pedido.familiaId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Familia no encontrada"));
		return libro.registrarRecaudacion(canal, familia, bloqueadas, pedido.operacion(), pedido.monto(),
				pedido.aCuenta(), new DatosComprobante(TipoComprobante.BOLETA, null, null, null), pedido.lineaId(),
				pedido.clave(), pedido.fechaComprobante(), pedido.detalle());
	}

	/** El pago ya registrado de una línea de recaudación (idempotencia: la misma línea dos veces). */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Pago> pagoDeLinea(Long lineaRecaudacionId) {
		return pagos.findByLineaRecaudacionId(lineaRecaudacionId);
	}

	/** Si la operación canónica ya está en un pago vigente (de cualquier medio digital). */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean operacionRegistrada(String operacion) {
		return operacion != null && pagos.existsByOperacionVigente(operacion);
	}

	/** El pago ya registrado de una orden (idempotencia: el mismo aviso dos veces). */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Pago> pagoDeOrden(Long ordenPagoId) {
		return pagos.findByOrdenPagoId(ordenPagoId);
	}
}
