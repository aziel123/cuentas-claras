package pe.edu.virgenmaria.cuentasclaras.recaudacion.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.DatosSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos.MotivoNoAplicable;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos.PedidoRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.CodigoPago;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLinea;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.EstadoLote;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LoteRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.MotivoExcepcion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LoteRecaudacionRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.IngresoRecaudacionAprobado;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.LoteConfirmado;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ReglasRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ReglasRecaudacion.Deuda;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ReglasRecaudacion.Resolucion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Aplica los pagos de un lote de recaudación ya CONFIRMADO a ciegas, como {@code sistema.recaudacion} (nunca una
 * persona), en la caja del canal RECAUDACION de cada fecha de pago y con la misma imputación, boleta y bitácora que la
 * ventanilla. Corre después del commit de la confirmación (en el mismo hilo) y, como respaldo, cada 5 minutos para los
 * lotes CONFIRMADO que quedaron a medias.
 * <ol>
 *   <li>Abre las cajas de canal de las fechas de pago (cada una en su transacción).</li>
 *   <li>En tandas de {@code lineas-por-transaccion}, cada tanda en su transacción: bloquea el lote y las cajas de la
 *       tanda (por fecha) y, línea por línea, bloquea las cuotas del alumno del código, decide con
 *       {@link ReglasRecaudacion} y vuelve a comprobar con {@code RegistroPagosAutomaticos.evaluar}: registra el pago con
 *       su boleta y deja la línea APLICADA, o la deja en EXCEPCION con su motivo (auditada y resaltada). Cada línea es
 *       idempotente: su pago es uno por línea (UNIQUE) y su clave se deriva de la línea.</li>
 *   <li>Al final, el lote queda APLICADO con sus conteos (y el resumen en la bitácora).</li>
 * </ol>
 * También registra el pago de una línea por revisar cuya aplicación aprobó otra persona ({@code APLICAR_INGRESO}).
 */
@Component
public class AplicadorRecaudacion {

	private static final Logger LOG = LoggerFactory.getLogger(AplicadorRecaudacion.class);

	/** Qué pasó con un lote. */
	public record Resumen(int aplicadas, int excepciones, int pendientes, boolean loteAplicado) {
	}

	private final LoteRecaudacionRepository lotes;

	private final LineaRecaudacionRepository lineas;

	private final CuotaRepository cuotas;

	private final SolicitudCambioRepository solicitudes;

	private final RegistroPagosAutomaticos registro;

	private final AuditoriaService auditoria;

	private final RecorridoColegios recorrido;

	private final PropiedadesRecaudacion propiedades;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AplicadorRecaudacion(LoteRecaudacionRepository lotes, LineaRecaudacionRepository lineas,
			CuotaRepository cuotas, SolicitudCambioRepository solicitudes, RegistroPagosAutomaticos registro,
			AuditoriaService auditoria, RecorridoColegios recorrido, PropiedadesRecaudacion propiedades,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.lotes = lotes;
		this.lineas = lineas;
		this.cuotas = cuotas;
		this.solicitudes = solicitudes;
		this.registro = registro;
		this.auditoria = auditoria;
		this.recorrido = recorrido;
		this.propiedades = propiedades;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	/** Primero (orden 0): la conciliación, que también escucha la confirmación del lote, verifica estos pagos. */
	@org.springframework.core.annotation.Order(0)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alConfirmarse(LoteConfirmado evento) {
		try {
			aplicar(evento.colegioId(), evento.loteId());
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo aplicar el lote de recaudación {}: {} (se reintentará)", evento.loteId(),
					e.getClass().getSimpleName());
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alAprobarse(IngresoRecaudacionAprobado evento) {
		try {
			aplicarRevision(evento.colegioId(), evento.lineaId(), evento.solicitudId());
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo aplicar la línea de recaudación {}: {} (se reintentará)", evento.lineaId(),
					e.getClass().getSimpleName());
		}
	}

	/** Respaldo: lotes CONFIRMADO que quedaron a medias y aplicaciones aprobadas sin pago, en cada colegio. */
	@Scheduled(fixedDelayString = "${cuentasclaras.recaudacion.aplicar-cada:5m}",
			initialDelayString = "${cuentasclaras.recaudacion.aplicar-cada:5m}")
	public void aplicarPendientes() {
		recorrido.enCadaColegio(ActorSistema.RECAUDACION, colegio -> {
			List<Long> confirmados = transaccion.execute(e -> lotes.findByEstadoOrderByIdAsc(EstadoLote.CONFIRMADO)
					.stream().map(LoteRecaudacion::getId).toList());
			Objects.requireNonNull(confirmados).forEach(this::aplicarEnContexto);
			aplicarRevisionesPendientes();
		});
	}

	/** Aplica el lote como {@code sistema.recaudacion} en su colegio. */
	public Resumen aplicar(long colegioId, Long loteId) {
		return EjecucionComoSistema.como(ActorSistema.RECAUDACION, colegioId, () -> aplicarEnContexto(loteId));
	}

	private Resumen aplicarEnContexto(Long loteId) {
		List<LocalDate> fechas = transaccion.execute(e -> lotes.findById(loteId)
				.filter(l -> l.getEstado() == EstadoLote.CONFIRMADO).map(l -> lineas.fechasPendientesDe(loteId))
				.orElse(List.of()));
		for (LocalDate fecha : Objects.requireNonNull(fechas)) {
			registro.prepararCanal(CanalCaja.RECAUDACION, fecha);
		}
		List<Long> pendientes = transaccion.execute(e -> lineas.pendientesDe(loteId));
		int tamano = propiedades.lineasPorTransaccion();
		for (int i = 0; i < Objects.requireNonNull(pendientes).size(); i += tamano) {
			List<Long> tanda = pendientes.subList(i, Math.min(i + tamano, pendientes.size()));
			try {
				transaccion.executeWithoutResult(e -> aplicarTanda(loteId, tanda));
			}
			catch (RuntimeException e) {
				// Una línea rompió la tanda: se aplican una por una para no frenar a las demás.
				LOG.warn("La tanda de recaudación del lote {} falló ({}): se aplica línea por línea.", loteId,
						e.getClass().getSimpleName());
				for (Long linea : tanda) {
					try {
						transaccion.executeWithoutResult(t -> aplicarTanda(loteId, List.of(linea)));
					}
					catch (RuntimeException f) {
						LOG.error("No se pudo aplicar la línea de recaudación {}: {} (queda pendiente)", linea,
								f.getClass().getSimpleName());
					}
				}
			}
		}
		return Objects.requireNonNull(transaccion.execute(e -> cerrarLote(loteId)));
	}

	private void aplicarTanda(Long loteId, List<Long> ids) {
		LoteRecaudacion lote = lotes.bloquear(loteId).orElseThrow();
		if (lote.getEstado() != EstadoLote.CONFIRMADO) {
			return;
		}
		List<LineaRecaudacion> porAplicar = lineas.findByIdInOrderByNumeroAsc(ids).stream()
				.filter(l -> l.getEstado() == EstadoLinea.PENDIENTE).toList();
		// Primero las cajas de la tanda, en orden de fecha (mismo orden de bloqueo para todos: sin interbloqueos).
		Map<LocalDate, CajaDiaria> cajas = new LinkedHashMap<>();
		new TreeSet<>(porAplicar.stream().map(LineaRecaudacion::getFechaPago).toList())
				.forEach(f -> cajas.put(f, registro.bloquearCanal(CanalCaja.RECAUDACION, f)));
		LocalDate hoy = LocalDate.now(reloj);
		for (LineaRecaudacion linea : porAplicar) {
			aplicarLinea(lote, linea, cajas.get(linea.getFechaPago()), hoy);
		}
	}

	private void aplicarLinea(LoteRecaudacion lote, LineaRecaudacion linea, CajaDiaria caja, LocalDate hoy) {
		if (registro.pagoDeLinea(linea.getId()).isPresent()) {
			linea.aplicar();
			return;
		}
		Long alumnoId = alumnoDe(linea);
		Long referidaId = linea.getCuota() == null ? null : linea.getCuota().getId();
		List<Long> ids = alumnoId == null ? List.of()
				: referidaId != null ? List.of(referidaId) : cuotas.idsPorPagarDeAlumno(alumnoId);
		// La primera lectura de las cuotas es la bloqueada (por id ascendente).
		List<Cuota> bloqueadas = ids.isEmpty() || !linea.enPesos() ? List.of() : registro.bloquearCuotas(ids);
		Map<Long, BigDecimal> saldos = new LinkedHashMap<>();
		List<Deuda> deudas = bloqueadas.stream().map(c -> new Deuda(c.getId(), c.getAlumno().getId(),
				c.getFechaVencimiento(), saldos.computeIfAbsent(c.getId(), id -> c.saldo()),
				c.admiteCobro() && !c.anulacionPendiente(), c.getDescripcion())).toList();
		Deuda referida = referidaId == null ? null
				: deudas.stream().filter(d -> d.cuotaId().equals(referidaId)).findFirst().orElse(null);
		Resolucion resolucion = ReglasRecaudacion.resolver(linea.getMonto(), linea.getMoneda(),
				registro.operacionRegistrada(linea.getNumeroOperacion()), alumnoId, referida,
				referidaId == null ? deudas : List.of(), propiedades.aceptarParciales());
		if (!resolucion.aplicable()) {
			excepcion(lote, linea, resolucion.motivo(), resolucion.detalle());
			return;
		}
		List<Cuota> elegidas = bloqueadas.stream().filter(c -> resolucion.cuotas().contains(c.getId())).toList();
		Long familiaId = linea.getAlumno().getFamilia().getId();
		Optional<MotivoNoAplicable> noAplicable = registro.evaluar(familiaId, elegidas, linea.getMonto(),
				linea.getNumeroOperacion(), null);
		if (noAplicable.isPresent()) {
			excepcion(lote, linea, motivoDe(noAplicable.get()), "No se pudo aplicar al registrarlo: "
					+ noAplicable.get().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ".");
			return;
		}
		Pago pago = registro.registrarRecaudacion(caja, elegidas, new PedidoRecaudacion(familiaId,
				linea.getNumeroOperacion(), linea.getMonto(), resolucion.aCuenta(), linea.getId(), claveDe(linea.getId()),
				linea.getFechaPago(), hoy, "Lote " + lote.getId() + ", línea " + linea.getNumero() + " (código "
						+ CodigoPago.legible(linea.getCodigo()) + "), confirmado a ciegas por " + lote.getConfirmadoPor()
						+ "." + (resolucion.aCuenta() ? " Pago a cuenta (parcial)." : "")));
		linea.aplicar();
		LOG.debug("Línea {} del lote {} aplicada con el pago {}", linea.getNumero(), lote.getId(), pago.getId());
	}

	private void excepcion(LoteRecaudacion lote, LineaRecaudacion linea, MotivoExcepcion motivo, String detalle) {
		linea.marcarExcepcion(motivo, detalle);
		auditoria.registrar(AccionAuditoria.RECAUDACION_LINEA_EXCEPCION, "linea_recaudacion", linea.getId().toString(),
				EstadoLinea.PENDIENTE.name(), EstadoLinea.EXCEPCION.name() + " · " + motivo.name(),
				"Lote " + lote.getId() + ", línea " + linea.getNumero() + ": pago del " + linea.getFechaPago() + " por "
						+ linea.getMoneda() + " " + linea.getMonto().toPlainString() + " (código "
						+ CodigoPago.legible(linea.getCodigo()) + ", operación " + linea.getNumeroOperacion()
						+ ") no se aplicó solo: " + motivo.descripcion() + ". " + Objects.toString(detalle, "")
						+ " Administración pide aplicarlo o devolverlo; aprueba otra persona.");
	}

	/** El alumno de la línea, solo si su código sigue correspondiendo a ese alumno (dígito verificador incluido). */
	private static Long alumnoDe(LineaRecaudacion linea) {
		if (linea.getAlumno() == null) {
			return null;
		}
		Long alumnoId = linea.getAlumno().getId();
		return CodigoPago.alumnoDe(linea.getCodigo()).filter(alumnoId::equals).orElse(null);
	}

	private static MotivoExcepcion motivoDe(MotivoNoAplicable motivo) {
		return switch (motivo) {
			case CUOTA_NO_COBRABLE, MONTO_CAMBIO -> MotivoExcepcion.CUOTA_NO_COBRABLE;
			case EXCESO -> MotivoExcepcion.EXCESO;
			case OPERACION_DUPLICADA -> MotivoExcepcion.OPERACION_DUPLICADA;
		};
	}

	/** Si ya no quedan líneas pendientes, el lote queda APLICADO con sus conteos. */
	private Resumen cerrarLote(Long loteId) {
		LoteRecaudacion lote = lotes.bloquear(loteId).orElseThrow();
		List<LineaRecaudacion> todas = lineas.findByLoteIdOrderByNumeroAsc(loteId);
		List<LineaRecaudacion> aplicadas = todas.stream().filter(l -> l.getEstado() == EstadoLinea.APLICADA).toList();
		List<LineaRecaudacion> pendientes = todas.stream().filter(l -> l.getEstado() == EstadoLinea.PENDIENTE).toList();
		List<LineaRecaudacion> otras = todas.stream().filter(l -> l.getEstado() != EstadoLinea.APLICADA
				&& l.getEstado() != EstadoLinea.PENDIENTE).toList();
		if (lote.getEstado() != EstadoLote.CONFIRMADO || !pendientes.isEmpty()) {
			return new Resumen(aplicadas.size(), otras.size(), pendientes.size(), lote.getEstado() == EstadoLote.APLICADO);
		}
		BigDecimal montoAplicado = Dinero.sumar(aplicadas.stream().map(LineaRecaudacion::getMonto).toList());
		BigDecimal montoExcepcion = Dinero.sumar(otras.stream().map(LineaRecaudacion::getMonto).toList());
		lote.marcarAplicado(aplicadas.size(), otras.size(), montoAplicado, montoExcepcion, ahora());
		auditoria.registrar(AccionAuditoria.RECAUDACION_APLICADA, "lote_recaudacion", loteId.toString(),
				EstadoLote.CONFIRMADO.name(), EstadoLote.APLICADO.name() + " · " + aplicadas.size() + " aplicadas · "
						+ otras.size() + " por revisar",
				"Recaudación del " + lote.getDesde() + " al " + lote.getHasta() + ": " + aplicadas.size() + " pagos por "
						+ Dinero.formatear(montoAplicado) + " registrados por el sistema con su boleta; " + otras.size()
						+ " líneas por " + Dinero.formatear(montoExcepcion) + " quedaron por revisar.");
		return new Resumen(aplicadas.size(), otras.size(), 0, true);
	}

	// --- Excepciones con aplicación aprobada (APLICAR_INGRESO) ---

	/** Registra el pago de una línea por revisar cuya aplicación aprobó otra persona. */
	public Optional<Long> aplicarRevision(long colegioId, Long lineaId, Long solicitudId) {
		return EjecucionComoSistema.como(ActorSistema.RECAUDACION, colegioId,
				() -> aplicarRevisionEnContexto(lineaId, solicitudId));
	}

	private void aplicarRevisionesPendientes() {
		List<Long[]> aprobadas = transaccion.execute(e -> {
			List<Long[]> pares = new ArrayList<>();
			for (LineaRecaudacion linea : lineas.findByEstadoOrderByIdAsc(EstadoLinea.EXCEPCION)) {
				solicitudes.findFirstByTipoAndEntidadAndEntidadIdAndEstadoOrderByIdDesc(TipoSolicitud.APLICAR_INGRESO,
						"linea_recaudacion", linea.getId(), EstadoSolicitud.APROBADA)
						.ifPresent(s -> pares.add(new Long[] { linea.getId(), s.getId() }));
			}
			return pares;
		});
		Objects.requireNonNull(aprobadas).forEach(par -> aplicarRevisionEnContexto(par[0], par[1]));
	}

	private Optional<Long> aplicarRevisionEnContexto(Long lineaId, Long solicitudId) {
		LocalDate fecha = transaccion.execute(e -> lineas.findById(lineaId).map(LineaRecaudacion::getFechaPago)
				.orElse(null));
		if (fecha == null) {
			return Optional.empty();
		}
		registro.prepararCanal(CanalCaja.RECAUDACION, fecha);
		return transaccion.execute(e -> {
			Long loteId = lineas.findById(lineaId).orElseThrow().getLote().getId();
			LoteRecaudacion lote = lotes.bloquear(loteId).orElseThrow();
			LineaRecaudacion linea = lineas.bloquear(lineaId).orElseThrow();
			if (linea.getEstado() != EstadoLinea.EXCEPCION) {
				return Optional.<Long>empty();
			}
			SolicitudCambio solicitud = solicitudes.findById(solicitudId)
					.filter(s -> s.getTipo() == TipoSolicitud.APLICAR_INGRESO && s.getEstado() == EstadoSolicitud.APROBADA
							&& "linea_recaudacion".equals(s.getEntidad()) && lineaId.equals(s.getEntidadId()))
					.orElseThrow(() -> new IllegalStateException("La aplicación no está aprobada"));
			Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
			Long destino = Long.valueOf(datos.get("familiaId"));
			List<Long> ids = Arrays.stream(datos.getOrDefault("cuotas", "").split(",")).filter(t -> !t.isBlank())
					.map(Long::valueOf).sorted().toList();
			CajaDiaria caja = registro.bloquearCanal(CanalCaja.RECAUDACION, linea.getFechaPago());
			List<Cuota> elegidas = registro.bloquearCuotas(ids);
			Optional<MotivoNoAplicable> motivo = registro.evaluar(destino, elegidas, linea.getMonto(),
					linea.getNumeroOperacion(), null);
			if (motivo.isPresent()) {
				LOG.warn("La línea de recaudación {} no se pudo aplicar ({}): sigue por revisar.", lineaId, motivo.get());
				return Optional.<Long>empty();
			}
			BigDecimal debe = Dinero.sumar(elegidas.stream().map(Cuota::saldo).toList());
			boolean aCuenta = debe.compareTo(linea.getMonto()) > 0;
			Long origen = linea.getAlumno() == null ? null : linea.getAlumno().getFamilia().getId();
			boolean otraFamilia = !destino.equals(origen);
			Pago pago = registro.registrarRecaudacion(caja, elegidas, new PedidoRecaudacion(destino,
					linea.getNumeroOperacion(), linea.getMonto(), aCuenta, lineaId, claveDe(lineaId), linea.getFechaPago(),
					LocalDate.now(reloj), "Línea por revisar aplicada con la solicitud " + solicitudId + " aprobada por "
							+ solicitud.getResueltoPor() + (otraFamilia ? ": el dinero va a OTRA familia." : ".")));
			linea.aplicarTrasRevision();
			auditoria.registrar(AccionAuditoria.INGRESO_APLICADO, "linea_recaudacion", lineaId.toString(),
					EstadoLinea.EXCEPCION.name(), EstadoLinea.APLICADA_REVISION.name(), "Se aplicó el pago por banco "
							+ "de la línea " + linea.getNumero() + " del lote " + lote.getId() + " por "
							+ Dinero.formatear(linea.getMonto()) + " con el pago " + pago.getId() + " ("
							+ pago.getComprobante().numeroCompleto() + ")" + (aCuenta ? ", a cuenta" : "")
							+ (otraFamilia ? ", a OTRA familia" : "") + ". Pedido por " + solicitud.getSolicitadoPor()
							+ ", aprobado por " + solicitud.getResueltoPor() + ".");
			return Optional.of(pago.getId());
		});
	}

	/** La clave del pago se deriva de la línea: la misma línea dos veces no puede crear dos pagos. */
	static UUID claveDe(Long lineaId) {
		return UUID.nameUUIDFromBytes(("linea_recaudacion:" + lineaId).getBytes(StandardCharsets.UTF_8));
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
