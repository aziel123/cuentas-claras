package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.ApoderadoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.FamiliaRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.config.PropiedadesCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.BusquedaCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ComprobanteImprimible;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConfirmacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CuentaFamilia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.LineaComprobanteVista;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.PagosDelDia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ResultadoBusqueda;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.RevisionCobro;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.SeleccionCobroRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.model.AplicacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.CuotaPorPagar;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.Imputacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ReglasEfectivo;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AnulacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.AplicacionPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoVisibleCuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.AperturaSerie;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Caja: buscar a la familia, revisar el cobro y cobrar. La cajera elige cuotas y medio; NUNCA escribe el monto (lo
 * calcula el sistema con los saldos) y solo escribe lo recibido en efectivo, para el vuelto.
 * <p>
 * Cobrar: abre la caja del día si hace falta (su propia transacción) y, en la transacción del cobro, bloquea la caja de
 * la cajera, busca la clave de idempotencia (el doble clic devuelve el mismo pago) y delega en {@link LibroPagos}.
 * Toda consulta la filtra el colegio de la sesión ({@code @TenantId}); un pago de otra caja es 404.
 */
@Service
@PreAuthorize("hasRole('CAJA')")
public class ServicioCobro {

	static final int MAX_RESULTADOS = 20;

	private static final int MAX_PALABRAS = 3;

	private final AlumnoRepository alumnos;

	private final ApoderadoRepository apoderados;

	private final FamiliaRepository familias;

	private final MatriculaRepository matriculas;

	private final CuotaRepository cuotas;

	private final PagoRepository pagos;

	private final AplicacionPagoRepository aplicaciones;

	private final CajaDiariaRepository cajas;

	private final AperturaCaja aperturaCaja;

	private final AperturaSerie aperturaSerie;

	private final LibroPagos libro;

	private final AuditoriaService auditoria;

	private final BusquedaFamilias busqueda;

	private final NombresUsuarios nombres;

	private final RegistroSolicitudes solicitudes;

	private final AnulacionPagoRepository anulaciones;

	private final ColegioService colegios;

	private final PropiedadesCaja propiedades;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public ServicioCobro(AlumnoRepository alumnos, ApoderadoRepository apoderados, FamiliaRepository familias,
			MatriculaRepository matriculas, CuotaRepository cuotas, PagoRepository pagos,
			AplicacionPagoRepository aplicaciones, CajaDiariaRepository cajas, AperturaCaja aperturaCaja,
			AperturaSerie aperturaSerie, LibroPagos libro, AuditoriaService auditoria, ColegioService colegios,
			PropiedadesCaja propiedades, PlatformTransactionManager transacciones, Clock reloj, BusquedaFamilias busqueda,
			NombresUsuarios nombres, RegistroSolicitudes solicitudes, AnulacionPagoRepository anulaciones) {
		this.busqueda = busqueda;
		this.nombres = nombres;
		this.solicitudes = solicitudes;
		this.anulaciones = anulaciones;
		this.alumnos = alumnos;
		this.apoderados = apoderados;
		this.familias = familias;
		this.matriculas = matriculas;
		this.cuotas = cuotas;
		this.pagos = pagos;
		this.aplicaciones = aplicaciones;
		this.cajas = cajas;
		this.aperturaCaja = aperturaCaja;
		this.aperturaSerie = aperturaSerie;
		this.libro = libro;
		this.auditoria = auditoria;
		this.colegios = colegios;
		this.propiedades = propiedades;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	// ------------------------------------------------------------------ búsqueda

	/** Alumnos o apoderados por nombre (hasta tres palabras, sin tildes) o por el inicio del DNI. Hasta 20 tarjetas. */
	@Transactional(readOnly = true)
	public BusquedaCaja buscar(String texto) {
		LocalDate anterior = cajaAnteriorAbierta(SesionCaja.usuario(), hoy());
		String limpio = Normalizador.limpiar(texto);
		if (limpio == null || limpio.length() < 2) {
			return new BusquedaCaja(limpio, List.of(), limpio != null, anterior);
		}
		List<ResultadoBusqueda> resultados = busqueda.buscar(limpio, hoy());
		return new BusquedaCaja(limpio, resultados, true, anterior);
	}

	// ------------------------------------------------------------------ cuenta de la familia

	/** Las cuotas por pagar de todos los hermanos y los pagos recientes de la familia. */
	@Transactional(readOnly = true)
	public CuentaFamilia cuentaDeFamilia(Long familiaId) {
		Familia familia = familia(familiaId);
		String cajero = SesionCaja.usuario();
		LocalDate hoy = hoy();
		Map<Long, List<Cuota>> porAlumno = cuotas.porPagarDeFamilia(familiaId).stream()
				.collect(Collectors.groupingBy(c -> c.getAlumno().getId(), LinkedHashMap::new, Collectors.toList()));
		List<CuentaFamilia.AlumnoCuotas> hijos = alumnos.findByFamiliaIdOrderByFechaNacimientoAsc(familiaId).stream()
				.filter(a -> a.activo() || porAlumno.containsKey(a.getId()))
				.map(a -> new CuentaFamilia.AlumnoCuotas(a.getId(), a.nombreCompleto(), grado(a),
						porAlumno.getOrDefault(a.getId(), List.of()).stream().map(c -> cuotaPorCobrar(c, hoy)).toList()))
				.toList();
		List<CuentaFamilia.PagoReciente> recientes = pagos.findTop5ByFamiliaIdOrderByIdDesc(familiaId).stream()
				.map(p -> new CuentaFamilia.PagoReciente(p.getFecha(), p.getComprobante().numeroCompleto(), p.getTotal(),
						p.getMedio().etiqueta(), p.vigente() ? "Vigente" : "Anulado"))
				.toList();
		boolean aceptaEfectivo = cajas.findByCajeroAndFecha(cajero, hoy).map(CajaDiaria::aceptaEfectivo).orElse(true);
		return new CuentaFamilia(familia.getId(), familia.getNombre(), hijos, recientes, aceptaEfectivo,
				cajaAnteriorAbierta(cajero, hoy), hoy);
	}

	private static CuentaFamilia.CuotaPorCobrar cuotaPorCobrar(Cuota c, LocalDate hoy) {
		EstadoVisibleCuota estado = c.estadoAl(hoy);
		return new CuentaFamilia.CuotaPorCobrar(c.getId(), c.getDescripcion(), c.getFechaVencimiento(), c.getMonto(),
				c.getMontoDescuento(), c.getMontoPagado(), c.saldo(), estado.etiqueta(), estado.variante(), c.admiteCobro(),
				c.anulacionPendiente() ? "Anulación por aprobar" : null, Dinero.enDecimos(c.saldo()));
	}

	// ------------------------------------------------------------------ revisión

	/**
	 * Solo lectura: total calculado por el sistema, imputación y a nombre de quién sale la boleta. Genera la clave de
	 * idempotencia del cobro ({@code clave} se conserva si se vuelve a mostrar tras un error).
	 */
	@Transactional(readOnly = true)
	public RevisionCobro revisar(Long familiaId, SeleccionCobroRequest seleccion, UUID clave) {
		Familia familia = familia(familiaId);
		LocalDate hoy = hoy();
		List<Cuota> porPagar = cuotas.porPagarDeFamilia(familiaId);
		Map<Long, Cuota> porId = porPagar.stream().collect(Collectors.toMap(Cuota::getId, Function.identity()));
		List<Long> ids = seleccion.cuotaIds().stream().filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			throw new ReglaNegocioException("Elige al menos una cuota.");
		}
		List<Cuota> elegidas = new ArrayList<>();
		for (Long id : ids) {
			Cuota cuota = porId.get(id);
			if (cuota == null) {
				throw new ReglaNegocioException("Alguna cuota elegida ya no está por pagar o no es de esta familia. "
						+ "Vuelve a elegir.");
			}
			if (!cuota.admiteCobro()) {
				throw new ReglaNegocioException("La cuota «" + cuota.getDescripcion() + "» tiene una anulación esperando "
						+ "aprobación: no se cobra hasta que se resuelva.");
			}
			elegidas.add(cuota);
		}
		BigDecimal total = Dinero.sumar(elegidas.stream().map(Cuota::saldo).toList());
		if (seleccion.medio() == MedioPago.EFECTIVO) {
			ReglasEfectivo.exigirDecimos(total, "total");
			boolean aceptaEfectivo = cajas.findByCajeroAndFecha(SesionCaja.usuario(), hoy)
					.map(CajaDiaria::aceptaEfectivo).orElse(true);
			if (!aceptaEfectivo) {
				throw new ReglaNegocioException("La caja de hoy ya se cerró: solo se aceptan pagos digitales hasta mañana.");
			}
		}
		List<Imputacion> imputaciones = ImputacionPago.imputar(total, elegidas.stream()
				.map(c -> new CuotaPorPagar(c.getId(), c.getFechaVencimiento(), c.saldo())).toList());
		List<RevisionCobro.LineaRevision> lineas = imputaciones.stream().map(i -> {
			Cuota c = porId.get(i.cuotaId());
			return new RevisionCobro.LineaRevision(c.getAlumno().nombreCompleto(), c.getDescripcion(),
					c.getFechaVencimiento(), i.monto());
		}).toList();
		LocalDate ultimoElegido = elegidas.stream().map(Cuota::getFechaVencimiento).max(Comparator.naturalOrder())
				.orElseThrow();
		List<String> avisos = porPagar.stream()
				.filter(c -> !ids.contains(c.getId()) && c.vencidaAl(hoy) && c.getFechaVencimiento().isBefore(ultimoElegido))
				.map(c -> c.getDescripcion() + " de " + c.getAlumno().nombreCompleto() + " (venció el "
						+ Calendario.formatear(c.getFechaVencimiento()) + ")")
				.toList();
		if (!avisos.isEmpty()) {
			avisos = List.of("Quedan cuotas vencidas más antiguas sin elegir: " + String.join("; ", avisos)
					+ ". El apoderado decide qué paga.");
		}
		Apoderado porDefecto = LibroPagos.responsablePorDefecto(elegidas);
		Set<Long> responsables = elegidas.stream().map(c -> c.getAlumno().getResponsablePago().getId())
				.collect(Collectors.toSet());
		List<RevisionCobro.OpcionReceptor> receptores = apoderados.findByFamiliaIdOrderByApellidoPaternoAsc(familiaId)
				.stream().filter(Apoderado::isActivo)
				.map(a -> new RevisionCobro.OpcionReceptor(a.getId(), a.nombreCompleto(), a.getDocumento().enmascarado(),
						responsables.contains(a.getId())))
				.toList();
		return new RevisionCobro(clave == null ? UUID.randomUUID() : clave, familia.getId(), familia.getNombre(),
				seleccion.medio(), elegidas.stream().map(Cuota::getId).toList(), lineas, total, receptores,
				porDefecto.getId(), avisos, propiedades.permitirPagoACuenta(), propiedades.pagoACuentaMinimo());
	}

	// ------------------------------------------------------------------ cobro

	/** Resultado de la transacción del cobro: el pago o el motivo de un rechazo que debe quedar auditado. */
	private record Resultado(Long pagoId, String rechazo) {
	}

	/**
	 * Registra el pago y emite su comprobante. Si la clave ya se usó (doble clic, recarga), devuelve el mismo pago.
	 *
	 * @return id del pago
	 */
	public Long cobrar(CobroRequest solicitud) {
		Objects.requireNonNull(solicitud, "solicitud");
		String cajero = SesionCaja.usuario();
		LocalDate hoy = hoy();
		prepararCajaYSerie(cajero, hoy, solicitud.comprobante());
		Resultado resultado;
		try {
			resultado = transaccion.execute(estado -> cobrarEnTransaccion(solicitud, cajero, hoy));
		}
		catch (DataIntegrityViolationException e) {
			String causa = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
			if (causa.contains("uk_pago_operacion")) {
				throw new ReglaNegocioException("Ese número de operación de " + solicitud.medio().etiqueta()
						+ " ya está registrado en otro pago. Revisa el número.");
			}
			if (causa.contains("uk_pago_idempotencia")) {
				return transaccion.execute(estado -> pagos.findByClaveIdempotencia(solicitud.clave().toString())
						.filter(p -> p.getCajero().equals(cajero)).map(Pago::getId).orElseThrow(() -> e));
			}
			throw e;
		}
		Objects.requireNonNull(resultado, "resultado");
		if (resultado.rechazo() != null) {
			throw new ReglaNegocioException(resultado.rechazo());
		}
		return resultado.pagoId();
	}

	/**
	 * Fuera de la transacción del cobro (así no pide una segunda conexión mientras tiene la primera): abre la caja del
	 * día y la serie del comprobante si aún no existen. Si otro cobro las crea a la vez, se usan esas.
	 */
	private void prepararCajaYSerie(String cajero, LocalDate hoy, TipoComprobante tipo) {
		try {
			aperturaCaja.asegurar(cajero, hoy);
		}
		catch (DataIntegrityViolationException otraLaAbrio) {
			// La abrió a la vez otro cobro de la misma cajera: se usa esa.
		}
		if (tipo == TipoComprobante.BOLETA || tipo == TipoComprobante.FACTURA) {
			try {
				aperturaSerie.crearSiFalta(tipo);
			}
			catch (DataIntegrityViolationException otroLaCreo) {
				// La creó a la vez otro cobro: se usa esa.
			}
		}
	}

	private Resultado cobrarEnTransaccion(CobroRequest solicitud, String cajero, LocalDate hoy) {
		// 1. La caja de la cajera, bloqueada: serializa sus cobros (el doble clic espera aquí).
		CajaDiaria caja = cajas.bloquear(cajero, hoy)
				.orElseThrow(() -> new IllegalStateException("La caja del día no se abrió"));
		// 2. La misma clave: es el mismo cobro (doble clic o recarga). Se devuelve ese pago.
		Pago existente = pagos.findByClaveIdempotencia(solicitud.clave().toString()).orElse(null);
		if (existente != null) {
			if (!existente.getCajero().equals(cajero)) {
				throw new ReglaNegocioException("Este cobro ya no es válido. Vuelve a revisarlo.");
			}
			return new Resultado(existente.getId(), null);
		}
		// 3. No se cobra con una caja anterior sin cerrar.
		LocalDate anterior = cajaAnteriorAbierta(cajero, hoy);
		if (anterior != null) {
			throw new ReglaNegocioException("Primero cierra tu caja del " + Calendario.formatear(anterior)
					+ ": no se puede cobrar con una caja anterior abierta.");
		}
		// 4. Una caja cerrada no recibe efectivo (queda auditado y la transacción se confirma con ese evento).
		if (solicitud.medio() == MedioPago.EFECTIVO && !caja.aceptaEfectivo()) {
			auditoria.registrar(AccionAuditoria.CAJA_EFECTIVO_RECHAZADO_CERRADA, "caja_diaria", caja.getId().toString(),
					caja.getEstado().name(), null, "Intentó cobrar en efectivo a la familia " + solicitud.familiaId()
							+ " con la caja del " + Calendario.formatear(hoy) + " cerrada.");
			return new Resultado(null, "La caja de hoy ya se cerró: solo se aceptan pagos digitales hasta mañana.");
		}
		Familia familia = familia(solicitud.familiaId());
		Pago pago = libro.registrar(caja, familia, new OrdenCobro(solicitud.cuotaIds(), solicitud.medio(),
				solicitud.numeroOperacion(), solicitud.recibido(), solicitud.montoACuenta(), solicitud.totalVisto(),
				new DatosComprobante(solicitud.comprobante(), solicitud.receptorApoderadoId(), solicitud.ruc(),
						solicitud.razonSocial()), solicitud.clave()));
		return new Resultado(pago.getId(), null);
	}

	// ------------------------------------------------------------------ después del cobro

	/** Confirmación de un pago de SU caja (si no, 404). */
	@Transactional(readOnly = true)
	public ConfirmacionPago confirmacion(Long pagoId) {
		Pago pago = pagoPropio(pagoId);
		Comprobante comprobante = pago.getComprobante();
		return new ConfirmacionPago(pago.getId(), comprobante.numeroCompleto(), comprobante.getTipo().etiqueta(),
				comprobante.getReceptor().nombre(), pago.getTotal(), pago.getMedio().etiqueta(), !pago.getMedio().digital(),
				pago.getRecibido(), pago.getVuelto(), pago.getNumeroOperacion(), pago.isACuenta(),
				comprobante.getProveedor() == ProveedorComprobantes.SIMULADO, comprobante.getEstadoEnvio().name(),
				pago.getFamilia().getNombre(), lineas(comprobante), pago.getCreadoEn());
	}

	/** El comprobante para imprimir y entregar al apoderado (solo pagos de SU caja; si no, 404). */
	@Transactional(readOnly = true)
	public ComprobanteImprimible imprimible(Long pagoId) {
		Pago pago = pagoPropio(pagoId);
		return imprimibleDe(pago.getComprobante(), pago, colegios.nombreDe(pago.getColegioId()), nombres, null);
	}

	/** El impreso de un comprobante (de su pago, si tiene) o de una nota de crédito (con el comprobante que anula). */
	static ComprobanteImprimible imprimibleDe(Comprobante c, Pago pago, String colegio, NombresUsuarios nombres,
			Comprobante anulado) {
		return new ComprobanteImprimible(pago == null ? null : pago.getId(), colegio, c.getTipo().etiqueta(),
				c.numeroCompleto(), c.getFechaEmision(), pago == null ? c.getCreadoEn() : pago.getCreadoEn(),
				c.getReceptor().nombre(), c.getReceptor().documentoTexto(), lineas(c), c.getTotal(), c.getMoneda(),
				c.getAfectacionIgv().etiqueta(), pago == null ? null : pago.getMedio().etiqueta(),
				pago == null ? null : pago.getRecibido(), pago == null ? null : pago.getVuelto(),
				pago == null ? null : pago.getNumeroOperacion(),
				nombres.de(pago == null ? c.getCreadoPor() : pago.getCajero()),
				c.getProveedor() == ProveedorComprobantes.SIMULADO, c.getCodigoHash(),
				anulado == null ? null : anulado.numeroCompleto(), c.getMotivoNota());
	}

	/** Los pagos de hoy de SU caja, sin totales: la cajera no ve el efectivo esperado con la caja abierta. */
	@Transactional(readOnly = true)
	public PagosDelDia pagosDelDia() {
		LocalDate hoy = hoy();
		String cajera = nombres.de(SesionCaja.usuario());
		CajaDiaria caja = cajas.findByCajeroAndFecha(SesionCaja.usuario(), hoy).orElse(null);
		if (caja == null) {
			return new PagosDelDia(hoy, cajera, List.of());
		}
		List<Pago> delDia = pagos.findByCajaIdOrderByIdDesc(caja.getId());
		Map<Long, List<AplicacionPago>> porPago = delDia.isEmpty() ? Map.of()
				: aplicaciones.dePagos(delDia.stream().map(Pago::getId).toList()).stream()
						.collect(Collectors.groupingBy(a -> a.getPago().getId()));
		Map<Long, String> notas = delDia.isEmpty() ? Map.of()
				: anulaciones.findByPagoIdIn(delDia.stream().map(Pago::getId).toList()).stream()
						.collect(Collectors.toMap(a -> a.getPago().getId(), a -> a.getNotaCredito().numeroCompleto()));
		Map<Long, String> comprobantesDelDia = delDia.stream()
				.collect(Collectors.toMap(Pago::getId, p -> p.getComprobante().numeroCompleto()));
		List<PagosDelDia.PagoDelDia> filas = delDia.stream().map(p -> {
			List<AplicacionPago> suyas = porPago.getOrDefault(p.getId(), List.of());
			String alumnosDelPago = suyas.stream().map(a -> a.getCuota().getAlumno().nombreCompleto()).distinct()
					.collect(Collectors.joining(", "));
			String conceptos = suyas.stream().map(a -> a.getCuota().getDescripcion()).distinct()
					.collect(Collectors.joining(", "));
			boolean pendiente = p.vigente() && !solicitudes.pendientesDe("pago", p.getId()).isEmpty();
			String estado = !p.vigente() ? "Anulado" : pendiente ? "Esperando aprobación" : "Vigente";
			String variante = !p.vigente() ? "neutro" : pendiente ? "alerta" : "exito";
			String reemplaza = p.getReemplazaPagoId() == null ? null
					: comprobantesDelDia.getOrDefault(p.getReemplazaPagoId(), "pago " + p.getReemplazaPagoId());
			return new PagosDelDia.PagoDelDia(p.getId(), p.getCreadoEn().toLocalTime().withNano(0),
					p.getComprobante().numeroCompleto(), alumnosDelPago, conceptos, p.getMedio().etiqueta(), p.getTotal(),
					estado, variante, !p.vigente(), p.vigente() && !pendiente, notas.get(p.getId()), reemplaza);
		}).toList();
		return new PagosDelDia(hoy, cajera, filas);
	}

	// ------------------------------------------------------------------ ayudas

	private Pago pagoPropio(Long pagoId) {
		return pagos.findById(pagoId).filter(p -> p.getCajero().equals(SesionCaja.usuario()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
	}

	private static List<LineaComprobanteVista> lineas(Comprobante comprobante) {
		return comprobante.getLineas().stream().map(l -> new LineaComprobanteVista(l.getDescripcion(), l.getMonto()))
				.toList();
	}

	private Familia familia(Long familiaId) {
		return familias.findById(familiaId).orElseThrow(() -> new RecursoNoEncontradoException("Familia no encontrada"));
	}

	private LocalDate cajaAnteriorAbierta(String cajero, LocalDate hoy) {
		return cajas.findFirstByCajeroAndEstadoAndFechaBeforeOrderByFechaAsc(cajero, EstadoCaja.ABIERTA, hoy)
				.map(CajaDiaria::getFecha).orElse(null);
	}

	private String grado(Alumno alumno) {
		return busqueda.grado(alumno);
	}

	private LocalDate hoy() {
		return LocalDate.now(reloj);
	}
}
