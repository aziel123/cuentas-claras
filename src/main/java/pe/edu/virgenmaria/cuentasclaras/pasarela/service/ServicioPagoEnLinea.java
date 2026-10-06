package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.SesionApoderado;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoVisibleCuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.config.PropiedadesPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.CuentaEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.EstadoOrdenVista;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.PagoEnLineaRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.RevisionPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.SeleccionPagoRequest;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPagoCuota;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.proceso.ProcesadorPagosEnLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoCuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pago en línea del apoderado (sprint 4). La familia sale SIEMPRE de su cuenta ({@link SesionApoderado}), nunca de un
 * parámetro: una cuota de otra familia es 404.
 * <ul>
 *   <li>El monto lo calcula el servidor: Σ de los saldos de las cuotas elegidas, bloqueadas al crear la orden.
 *       {@code totalVisto} solo detecta que el saldo cambió. No hay pago parcial en línea y hay un máximo por orden.</li>
 *   <li>Una cuota con otra orden en curso no entra a una orden nueva; si es exactamente la misma selección, se
 *       reutiliza esa orden.</li>
 *   <li>La orden se crea en la pasarela DESPUÉS del commit, fuera de la transacción. Si la pasarela falla, la orden
 *       queda sin enlace, vence sola y nada se cobró.</li>
 *   <li>Volver de la pasarela no marca nada como pagado: el estado se confirma solo CONSULTANDO a la pasarela.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasRole('APODERADO')")
public class ServicioPagoEnLinea {

	private static final Logger LOG = LoggerFactory.getLogger(ServicioPagoEnLinea.class);

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	private final SesionApoderado sesion;

	private final AlumnoRepository alumnos;

	private final CuotaRepository cuotas;

	private final PagoRepository pagos;

	private final OrdenPagoRepository ordenes;

	private final OrdenPagoCuotaRepository cuotasDeOrden;

	private final Pasarelas pasarelas;

	private final ProcesadorPagosEnLinea procesador;

	private final AuditoriaService auditoria;

	private final PropiedadesPasarela propiedades;

	private final TransactionTemplate escritura;

	private final TransactionTemplate lectura;

	private final Clock reloj;

	public ServicioPagoEnLinea(SesionApoderado sesion, AlumnoRepository alumnos, CuotaRepository cuotas,
			PagoRepository pagos, OrdenPagoRepository ordenes, OrdenPagoCuotaRepository cuotasDeOrden, Pasarelas pasarelas,
			ProcesadorPagosEnLinea procesador, AuditoriaService auditoria, PropiedadesPasarela propiedades,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.sesion = sesion;
		this.alumnos = alumnos;
		this.cuotas = cuotas;
		this.pagos = pagos;
		this.ordenes = ordenes;
		this.cuotasDeOrden = cuotasDeOrden;
		this.pasarelas = pasarelas;
		this.procesador = procesador;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.escritura = new TransactionTemplate(transacciones);
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.lectura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	// ------------------------------------------------------------------ estado de cuenta

	/** Las cuotas por pagar de SU familia, el código de pago de cada hijo, sus pagos en línea y sus comprobantes. */
	public CuentaEnLinea cuenta() {
		return lectura.execute(t -> {
			Apoderado apoderado = sesion.apoderado();
			Long familiaId = apoderado.getFamilia().getId();
			LocalDate hoy = LocalDate.now(reloj);
			LocalDateTime ahora = LocalDateTime.now(reloj);
			List<Cuota> porPagar = cuotas.porPagarDeFamilia(familiaId);
			Set<Long> enCurso = new HashSet<>();
			if (!porPagar.isEmpty()) {
				for (OrdenPago abierta : ordenes.abiertasConCuotas(porPagar.stream().map(Cuota::getId).toList(), ahora)) {
					cuotasDeOrden.findByOrdenIdOrderByIdAsc(abierta.getId()).forEach(f -> enCurso.add(f.getCuota().getId()));
				}
			}
			Map<Long, List<Cuota>> porAlumno = porPagar.stream().collect(Collectors.groupingBy(c -> c.getAlumno().getId(),
					LinkedHashMap::new, Collectors.toList()));
			List<CuentaEnLinea.Hijo> hijos = alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(familiaId).stream()
					.filter(a -> a.activo() || porAlumno.containsKey(a.getId()))
					.map(a -> new CuentaEnLinea.Hijo(a.getId(), a.nombreCompleto(), CodigoPago.legible(
							CodigoPago.deAlumno(a.getId())), porAlumno.getOrDefault(a.getId(), List.of()).stream()
									.map(c -> cuotaPorPagar(c, hoy, enCurso.contains(c.getId()))).toList()))
					.toList();
			BigDecimal total = Dinero.sumar(porPagar.stream().map(Cuota::saldo).toList());
			List<CuentaEnLinea.OrdenReciente> recientes = ordenes.findTop10ByFamiliaIdOrderByIdDesc(familiaId).stream()
					.map(o -> new CuentaEnLinea.OrdenReciente(o.getReferencia(), o.getCreadoEn(), o.getMonto(),
							o.getEstado().etiqueta(), o.getEstado().variante()))
					.toList();
			List<CuentaEnLinea.PagoReciente> recientesPagos = pagos.findTop5ByFamiliaIdOrderByIdDesc(familiaId).stream()
					.map(p -> new CuentaEnLinea.PagoReciente(p.getComprobante().getId(), p.getComprobante().numeroCompleto(),
							p.getFecha(), p.getTotal(), p.getMedio().etiqueta(), p.vigente() ? "Vigente" : "Anulado"))
					.toList();
			return new CuentaEnLinea(apoderado.getFamilia().getNombre(), apoderado.nombreCompleto(), hijos, total,
					recientes, recientesPagos, pasarelas.configurada().isPresent(), pasarelas.simulada(),
					propiedades.montoMaximo(), hoy);
		});
	}

	private static CuentaEnLinea.CuotaPorPagar cuotaPorPagar(Cuota c, LocalDate hoy, boolean enCurso) {
		EstadoVisibleCuota estado = c.estadoAl(hoy);
		String aviso = c.anulacionPendiente() ? "En revisión en el colegio"
				: enCurso ? "Tiene un pago en línea en curso" : null;
		return new CuentaEnLinea.CuotaPorPagar(c.getId(), c.getDescripcion(), c.getFechaVencimiento(), c.saldo(),
				estado.etiqueta(), estado.variante(), c.admiteCobro() && !enCurso, aviso);
	}

	// ------------------------------------------------------------------ revisión

	/** Solo lectura: el total que calcula el servidor, las cuotas y a nombre de quién sale el comprobante. */
	public RevisionPagoEnLinea revisar(SeleccionPagoRequest seleccion) {
		pasarelas.activa();
		return lectura.execute(t -> {
			Apoderado apoderado = sesion.apoderado();
			List<Cuota> elegidas = cuotasDeSuFamilia(apoderado, seleccion.cuotaIds(), false);
			BigDecimal total = Dinero.sumar(elegidas.stream().map(Cuota::saldo).toList());
			exigirMaximo(total);
			List<RevisionPagoEnLinea.Linea> lineas = elegidas.stream()
					.map(c -> new RevisionPagoEnLinea.Linea(c.getAlumno().nombreCompleto(), c.getDescripcion(),
							c.getFechaVencimiento(), c.saldo()))
					.toList();
			return new RevisionPagoEnLinea(UUID.randomUUID(), elegidas.stream().map(Cuota::getId).toList(), lineas, total,
					apoderado.nombreCompleto(), apoderado.getDocumento().enmascarado(), apoderado.getRuc() != null,
					apoderado.getRazonSocial(), pasarelas.simulada());
		});
	}

	// ------------------------------------------------------------------ orden

	/**
	 * Crea la orden (o devuelve la misma si se envió dos veces, o la orden abierta con esas mismas cuotas) y la crea en la
	 * pasarela fuera de la transacción.
	 *
	 * @return la referencia pública de la orden
	 */
	public String crearOrden(PagoEnLineaRequest pedido) {
		Objects.requireNonNull(pedido, "pedido");
		PasarelaPagos pasarela = pasarelas.activa();
		Creada creada = Objects.requireNonNull(escritura.execute(t -> crearEnTransaccion(pedido, pasarela.proveedor())));
		if (creada.nueva()) {
			solicitarEnlace(pasarela, creada.ordenId(), creada.colegioId());
		}
		return creada.referencia();
	}

	private record Creada(Long ordenId, String referencia, Long colegioId, boolean nueva) {
	}

	private Creada crearEnTransaccion(PagoEnLineaRequest pedido, ProveedorPasarela proveedor) {
		Apoderado apoderado = sesion.apoderado();
		Long familiaId = apoderado.getFamilia().getId();
		OrdenPago misma = ordenes.findByClaveIdempotencia(pedido.clave().toString()).orElse(null);
		if (misma != null) {
			if (!Objects.equals(misma.getFamilia().getId(), familiaId)) {
				throw new RecursoNoEncontradoException("Pago no encontrado");
			}
			return new Creada(misma.getId(), misma.getReferencia(), misma.getColegioId(), false);
		}
		// Bloquea las cuotas (por id): dos órdenes a la vez sobre la misma cuota se serializan aquí.
		List<Cuota> elegidas = cuotasDeSuFamilia(apoderado, pedido.cuotaIds(), true);
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		List<Long> ids = elegidas.stream().map(Cuota::getId).toList();
		for (OrdenPago abierta : ordenes.abiertasConCuotas(ids, ahora)) {
			Set<Long> suyas = cuotasDeOrden.findByOrdenIdOrderByIdAsc(abierta.getId()).stream()
					.map(f -> f.getCuota().getId()).collect(Collectors.toSet());
			if (suyas.equals(Set.copyOf(ids)) && Objects.equals(abierta.getFamilia().getId(), familiaId)) {
				return new Creada(abierta.getId(), abierta.getReferencia(), abierta.getColegioId(), false);
			}
			throw new ReglaNegocioException("Ya hay un pago en línea en curso para alguna de esas cuotas. Termínalo o "
					+ "espera a que venza a las " + HORA.format(abierta.getVenceEn()) + ".");
		}
		BigDecimal total = Dinero.sumar(elegidas.stream().map(Cuota::saldo).toList());
		if (pedido.totalVisto() == null || pedido.totalVisto().compareTo(total) != 0) {
			throw new ReglaNegocioException("El monto cambió desde que lo revisaste: ahora es " + Dinero.formatear(total)
					+ ". Revisa de nuevo antes de pagar.");
		}
		exigirMaximo(total);
		TipoComprobante tipo = TipoComprobante.BOLETA;
		if (pedido.quiereFactura()) {
			if (apoderado.getRuc() == null) {
				throw new ReglaNegocioException("No tienes un RUC registrado en el colegio: tu comprobante será una boleta.");
			}
			tipo = TipoComprobante.FACTURA;
		}
		OrdenPago orden = ordenes.save(OrdenPago.crear(apoderado.getFamilia(), apoderado, proveedor, total, tipo,
				pedido.clave(), ahora.plusMinutes(propiedades.minutosVigenciaOrden())));
		for (Cuota cuota : elegidas) {
			cuotasDeOrden.save(OrdenPagoCuota.de(orden, cuota));
		}
		auditoria.registrar(AccionAuditoria.ORDEN_PAGO_CREADA, "orden_pago", orden.getId().toString(), null,
				"CREADA · " + Dinero.formatear(total), "Pago en línea iniciado por el apoderado " + apoderado.nombreCompleto()
						+ " (" + apoderado.getDocumento().enmascarado() + ") de " + apoderado.getFamilia().getNombre()
						+ " por " + Dinero.formatear(total) + " con " + proveedor.name() + " (" + tipo.etiqueta() + "). Cuotas: "
						+ String.join("; ", elegidas.stream().map(c -> c.getDescripcion() + " de "
								+ c.getAlumno().nombreCompleto() + " (" + Dinero.formatear(c.saldo()) + ")").toList())
						+ ". Vence a las " + HORA.format(orden.getVenceEn()) + "."
						+ (proveedor == ProveedorPasarela.SIMULADA ? " PAGO SIMULADO · NO ES DINERO REAL." : ""));
		return new Creada(orden.getId(), orden.getReferencia(), orden.getColegioId(), true);
	}

	/** Pide a la pasarela la orden y su enlace, FUERA de la transacción, y guarda el enlace en otra. */
	private void solicitarEnlace(PasarelaPagos pasarela, Long ordenId, Long colegioId) {
		OrdenPago orden = lectura.execute(t -> ordenes.findById(ordenId).orElseThrow());
		OrdenCreada creada;
		try {
			creada = pasarela.crearOrden(new SolicitudOrden(orden.getReferencia(), orden.getMonto(),
					orden.getMonto().movePointRight(2).longValueExact(), orden.getMoneda(),
					"Pago Colegio Virgen María · " + orden.getReferencia(), orden.getVenceEn(),
					"/familia/pagos/" + orden.getReferencia(), colegioId));
		}
		catch (RuntimeException e) {
			LOG.warn("La pasarela no creó la orden {}: {}. Vence sola; nada se cobró.", ordenId, e.getClass().getSimpleName());
			return;
		}
		escritura.executeWithoutResult(t -> ordenes.bloquear(ordenId).orElseThrow()
				.registrarEnlace(creada.proveedorOrdenId(), creada.urlPago()));
	}

	/**
	 * El estado de una orden de SU familia (si no, 404). Si está en curso, consulta a la pasarela (como mucho una vez cada
	 * 10 segundos): volver de la pasarela no basta para marcarla pagada.
	 */
	public EstadoOrdenVista estado(String referencia) {
		OrdenPago orden = ordenDeSuFamilia(referencia);
		if ((orden.getEstado() == EstadoOrden.CREADA || orden.getEstado() == EstadoOrden.VENCIDA)
				&& orden.getProveedorOrdenId() != null) {
			procesador.procesarSiToca(orden.getColegioId(), orden.getId(), propiedades.consultaMinima());
		}
		return lectura.execute(t -> vista(ordenes.findById(orden.getId()).orElseThrow()));
	}

	/** El id de la orden en la pasarela, solo de SU familia (para el simulador). */
	public String idEnPasarela(String referencia) {
		OrdenPago orden = ordenDeSuFamilia(referencia);
		if (orden.getProveedorOrdenId() == null) {
			throw new ReglaNegocioException("No pudimos conectar con la pasarela. Intenta en unos minutos.");
		}
		return orden.getProveedorOrdenId();
	}

	private OrdenPago ordenDeSuFamilia(String referencia) {
		return lectura.execute(t -> {
			Long familiaId = sesion.familiaId();
			return ordenes.findByReferencia(referencia == null ? "" : referencia)
					.filter(o -> Objects.equals(o.getFamilia().getId(), familiaId))
					.orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
		});
	}

	private EstadoOrdenVista vista(OrdenPago orden) {
		List<String> lineas = cuotasDeOrden.findByOrdenIdOrderByIdAsc(orden.getId()).stream()
				.map(f -> f.getCuota().getDescripcion() + " · " + f.getCuota().getAlumno().nombreCompleto() + " · "
						+ Dinero.formatear(f.getMonto()))
				.toList();
		Pago pago = pagos.findByOrdenPagoId(orden.getId()).orElse(null);
		LocalDateTime ahora = LocalDateTime.now(reloj);
		boolean enCurso = orden.getEstado() == EstadoOrden.CREADA && ahora.isBefore(orden.getVenceEn());
		String mensaje = switch (orden.getEstado()) {
			case CREADA -> orden.getProveedorOrdenId() == null
					? "No pudimos conectar con la pasarela. Intenta en unos minutos: no se cobró nada."
					: enCurso ? "Te llevaremos a la página segura de la pasarela para pagar con Yape, Plin o tarjeta. Este "
							+ "enlace vence a las " + HORA.format(orden.getVenceEn()) + "."
							: "Estamos confirmando tu pago con la pasarela…";
			case PAGADA, APLICADA -> pago == null ? "Pago confirmado." : "Pago confirmado · "
					+ pago.getComprobante().getTipo().etiqueta() + " " + pago.getComprobante().numeroCompleto();
			case POR_REVISAR -> "Recibimos tu pago, pero el colegio debe revisarlo antes de aplicarlo (por ejemplo, la "
					+ "cuota ya estaba pagada). Te llamarán para devolverlo o aplicarlo a otra cuota.";
			case VENCIDA -> "El enlace venció sin que se completara el pago. No se cobró nada.";
			case RECHAZADA -> "No se completó el pago. No se cobró nada.";
			case DEVUELTA -> "Tu pago fue devuelto al mismo medio con el que pagaste.";
		};
		return new EstadoOrdenVista(orden.getReferencia(), orden.getEstado().name(), orden.getEstado().etiqueta(),
				orden.getEstado().variante(), orden.getMonto(), enCurso ? orden.getEnlacePago() : null, orden.getVenceEn(),
				lineas, pago == null ? null : pago.getComprobante().numeroCompleto(),
				pago == null ? null : pago.getComprobante().getId(), pago == null ? null : pago.getMedio().etiqueta(),
				orden.getEstado() == EstadoOrden.CREADA, orden.getProveedor() == ProveedorPasarela.SIMULADA,
				orden.isTardia(), mensaje);
	}

	// ------------------------------------------------------------------ ayudas

	/**
	 * Las cuotas elegidas, todas de SU familia (si alguna no lo es o no existe: 404) y por pagar. Con {@code bloquear}, en
	 * orden de id y con {@code SELECT ... FOR UPDATE}.
	 */
	private List<Cuota> cuotasDeSuFamilia(Apoderado apoderado, List<Long> cuotaIds, boolean bloquear) {
		List<Long> ids = cuotaIds == null ? List.of()
				: cuotaIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
		if (ids.isEmpty()) {
			throw new ReglaNegocioException("Elige al menos una cuota.");
		}
		List<Cuota> encontradas = bloquear ? cuotas.bloquear(ids)
				: ids.stream().map(id -> cuotas.findById(id).orElse(null)).filter(Objects::nonNull).toList();
		Long familiaId = apoderado.getFamilia().getId();
		if (encontradas.size() != ids.size()
				|| encontradas.stream().anyMatch(c -> !Objects.equals(c.getAlumno().getFamilia().getId(), familiaId))) {
			throw new RecursoNoEncontradoException("Cuota no encontrada");
		}
		Map<Long, Cuota> porId = encontradas.stream().collect(Collectors.toMap(Cuota::getId, Function.identity()));
		List<Cuota> elegidas = new ArrayList<>();
		for (Long id : ids) {
			Cuota cuota = porId.get(id);
			if (!cuota.admiteCobro() || cuota.saldo().signum() <= 0) {
				throw new ReglaNegocioException("La cuota «" + cuota.getDescripcion() + "» de " + cuota.getAlumno()
						.nombreCompleto() + " ya no está por pagar. Vuelve a tu estado de cuenta.");
			}
			elegidas.add(cuota);
		}
		elegidas.sort(java.util.Comparator.comparing(Cuota::getFechaVencimiento).thenComparing(Cuota::getId));
		return elegidas;
	}

	private void exigirMaximo(BigDecimal total) {
		if (total.compareTo(propiedades.montoMaximo()) > 0) {
			throw new ReglaNegocioException("El pago en línea es de hasta " + Dinero.formatear(propiedades.montoMaximo())
					+ " por vez. Elige menos cuotas y paga el resto en otro pago.");
		}
	}
}
