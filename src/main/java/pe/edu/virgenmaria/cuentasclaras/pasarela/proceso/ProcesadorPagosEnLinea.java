package pe.edu.virgenmaria.cuentasclaras.pasarela.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.DatosComprobante;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos.MotivoNoAplicable;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos.PedidoPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.CobroConfirmado;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoCobro;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoEvento;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EventoPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.MotivoRevision;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPagoCuota;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.ProveedorPasarela;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.EventoPasarelaRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoCuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.Pasarelas;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registra los pagos en línea como {@code sistema.pasarela}, SOLO con lo que responde la consulta a la pasarela (nunca
 * con lo que dice un aviso ni con el retorno del navegador).
 * <ol>
 *   <li>Sin transacción: consulta la pasarela con la llave secreta.</li>
 *   <li>PAGADO: abre la caja del canal del día (su propia transacción) y, en UNA transacción: bloquea la orden, la caja
 *       y las cuotas; registra la confirmación con {@code saveAndFlush} (el trigger del pago la lee); si el monto o la
 *       moneda difieren, o si el dinero ya no se puede aplicar (cuota pagada en caja, saldo cambiado, operación usada),
 *       la orden queda POR_REVISAR y no se aplica nada; si no, registra el pago con su boleta y la orden queda PAGADA.</li>
 *   <li>RECHAZADO, EXPIRADO o PENDIENTE vencido: la orden se rechaza o vence; nada se cobra.</li>
 * </ol>
 * El mismo aviso dos veces no duplica nada: la orden ya es final, el pago es uno por orden (UNIQUE) y la clave del pago
 * se deriva de la orden.
 */
@Component
public class ProcesadorPagosEnLinea {

	private static final Logger LOG = LoggerFactory.getLogger(ProcesadorPagosEnLinea.class);

	/** Qué pasó al procesar una orden. */
	public enum Resultado {
		PAGADA, POR_REVISAR, VENCIDA, RECHAZADA, SIN_CAMBIOS, IGNORADA, CONTRACARGO, ERROR
	}

	private final OrdenPagoRepository ordenes;

	private final OrdenPagoCuotaRepository cuotasDeOrden;

	private final EventoPasarelaRepository eventos;

	private final Pasarelas pasarelas;

	private final RegistroPagosAutomaticos registro;

	private final ServicioAnulacionPagos anulaciones;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	/** Última consulta por orden (en memoria; una sola instancia): la pantalla del apoderado no consulta más de 1 vez cada 10 s. */
	private final Map<Long, Instant> ultimaConsulta = new ConcurrentHashMap<>();

	public ProcesadorPagosEnLinea(OrdenPagoRepository ordenes, OrdenPagoCuotaRepository cuotasDeOrden,
			EventoPasarelaRepository eventos, Pasarelas pasarelas, RegistroPagosAutomaticos registro,
			ServicioAnulacionPagos anulaciones, AuditoriaService auditoria, PlatformTransactionManager transacciones,
			Clock reloj) {
		this.ordenes = ordenes;
		this.cuotasDeOrden = cuotasDeOrden;
		this.eventos = eventos;
		this.pasarelas = pasarelas;
		this.registro = registro;
		this.anulaciones = anulaciones;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	/** Procesa la orden como {@code sistema.pasarela} en su colegio. */
	public Resultado procesar(long colegioId, Long ordenId) {
		return EjecucionComoSistema.como(ActorSistema.PASARELA, colegioId, () -> procesarEnContexto(ordenId));
	}

	/**
	 * Lo mismo, pero solo si no se consultó hace menos de {@code minimo} (la pantalla del apoderado se recarga cada 5 s).
	 */
	public Resultado procesarSiToca(long colegioId, Long ordenId, java.time.Duration minimo) {
		Instant ahora = reloj.instant();
		Instant anterior = ultimaConsulta.get(ordenId);
		if (anterior != null && anterior.plus(minimo).isAfter(ahora)) {
			return Resultado.SIN_CAMBIOS;
		}
		return procesar(colegioId, ordenId);
	}

	/** Procesa un aviso ya guardado como {@code sistema.pasarela}: consulta la orden y deja el evento resuelto. */
	public Resultado procesarEvento(long colegioId, Long eventoId) {
		return EjecucionComoSistema.como(ActorSistema.PASARELA, colegioId, () -> {
			EventoPasarela evento = transaccion.execute(e -> eventos.findById(eventoId).orElse(null));
			if (evento == null || evento.getEstado() != EstadoEvento.RECIBIDO) {
				return Resultado.IGNORADA;
			}
			Resultado resultado = evento.getOrdenPagoId() == null ? Resultado.IGNORADA
					: procesarEnContexto(evento.getOrdenPagoId());
			transaccion.executeWithoutResult(e -> {
				EventoPasarela fila = eventos.findById(eventoId).orElseThrow();
				LocalDateTime ahora = ahora();
				switch (resultado) {
					case ERROR -> fila.fallo("No se pudo consultar o registrar el pago: se reintentará.", ahora);
					case IGNORADA -> fila.terminar(EstadoEvento.IGNORADO, evento.getOrdenPagoId() == null
							? "La orden del aviso no existe en este colegio." : "La orden ya estaba resuelta.", ahora);
					default -> fila.terminar(EstadoEvento.PROCESADO, "Resultado: " + resultado.name(), ahora);
				}
			});
			return resultado;
		});
	}

	/** Procesa la orden con el actor y el colegio YA fijados (lo usan los procesos de este paquete). */
	Resultado procesarEnContexto(Long ordenId) {
		ultimaConsulta.put(ordenId, reloj.instant());
		Instantanea orden = transaccion.execute(e -> ordenes.findById(ordenId).map(Instantanea::de).orElse(null));
		if (orden == null) {
			return Resultado.IGNORADA;
		}
		if (orden.estado() != EstadoOrden.PAGADA && orden.estado().finalizada()) {
			return Resultado.IGNORADA;
		}
		if (orden.estado() == EstadoOrden.POR_REVISAR) {
			return Resultado.IGNORADA;
		}
		if (orden.proveedorOrdenId() == null) {
			// Nunca llegó a la pasarela (falló al crearla): no hay nada que consultar. Vence sola; nada se cobró.
			return orden.estado() == EstadoOrden.CREADA && !ahora().isBefore(orden.venceEn()) ? vencer(ordenId)
					: Resultado.SIN_CAMBIOS;
		}
		EstadoCobro estado;
		try {
			estado = pasarelas.de(orden.proveedor()).consultar(orden.proveedorOrdenId());
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo consultar la orden {} en la pasarela: {}", ordenId, e.getClass().getSimpleName());
			return Resultado.ERROR;
		}
		try {
			return switch (estado.estado()) {
				case PAGADO -> orden.estado() == EstadoOrden.PAGADA ? Resultado.IGNORADA : aplicar(ordenId, estado.cobro());
				case RECHAZADO -> orden.estado() == EstadoOrden.CREADA ? rechazar(ordenId) : Resultado.SIN_CAMBIOS;
				case EXPIRADO -> orden.estado() == EstadoOrden.CREADA ? vencer(ordenId) : Resultado.SIN_CAMBIOS;
				case PENDIENTE -> orden.estado() == EstadoOrden.CREADA && !ahora().isBefore(orden.venceEn())
						? vencer(ordenId) : Resultado.SIN_CAMBIOS;
				case CONTRACARGO -> orden.estado() == EstadoOrden.PAGADA ? contracargo(ordenId) : Resultado.SIN_CAMBIOS;
			};
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo registrar el resultado de la orden {}: {} ({})", ordenId, e.getClass().getSimpleName(),
					e.getMessage());
			return Resultado.ERROR;
		}
	}

	private Resultado aplicar(Long ordenId, CobroConfirmado cobro) {
		CobroConfirmado canonico = new CobroConfirmado(cobro.cargoId(), NumeroOperacion.normalizar(cobro.operacionCanonica()),
				Dinero.normalizar(cobro.monto()), cobro.moneda(), cobro.medio(), cobro.pagadoEn());
		LocalDate fechaCanal = canonico.pagadoEn().toLocalDate();
		registro.prepararCanal(CanalCaja.PASARELA, fechaCanal);
		return transaccion.execute(e -> {
			OrdenPago orden = ordenes.bloquear(ordenId).orElseThrow();
			if (!orden.getEstado().admiteConfirmacion()) {
				return Resultado.IGNORADA;
			}
			boolean tardia = orden.getEstado() == EstadoOrden.VENCIDA;
			CajaDiaria canal = registro.bloquearCanal(CanalCaja.PASARELA, fechaCanal);
			List<OrdenPagoCuota> filas = cuotasDeOrden.findByOrdenIdOrderByIdAsc(ordenId);
			List<Cuota> cuotas = registro.bloquearCuotas(filas.stream().map(f -> f.getCuota().getId()).toList());
			// La confirmación sale ANTES del INSERT del pago: trg_pago_registro la lee (hallazgo 6).
			orden.registrarConfirmacion(canonico);
			ordenes.saveAndFlush(orden);
			Optional<MotivoRevision> motivo = Optional.empty();
			String detalle = null;
			if (canonico.monto().compareTo(orden.getMonto()) != 0) {
				motivo = Optional.of(MotivoRevision.MONTO_DISTINTO);
				detalle = "La pasarela confirmó " + Dinero.formatear(canonico.monto()) + " y la orden era por "
						+ Dinero.formatear(orden.getMonto()) + ".";
			}
			else if (!OrdenPago.MONEDA.equals(canonico.moneda())) {
				motivo = Optional.of(MotivoRevision.MONEDA_DISTINTA);
				detalle = "La pasarela confirmó el pago en " + canonico.moneda() + ".";
			}
			else {
				Map<Long, BigDecimal> esperados = new LinkedHashMap<>();
				filas.forEach(f -> esperados.put(f.getCuota().getId(), f.getMonto()));
				Optional<MotivoNoAplicable> noAplicable = registro.evaluar(orden.getFamilia().getId(), cuotas,
						canonico.monto(), canonico.operacionCanonica(), esperados);
				if (noAplicable.isPresent()) {
					motivo = Optional.of(MotivoRevision.valueOf(noAplicable.get().name()));
					detalle = detalleNoAplicable(noAplicable.get(), cuotas, canonico.operacionCanonica());
				}
			}
			if (motivo.isPresent()) {
				orden.marcarPorRevisar(motivo.get(), detalle);
				auditoria.registrar(AccionAuditoria.ORDEN_PAGO_POR_REVISAR, "orden_pago", ordenId.toString(),
						tardia ? "VENCIDA" : "CREADA", "POR_REVISAR", "Pago en línea de " + orden.getFamilia().getNombre()
								+ " por " + Dinero.formatear(canonico.monto()) + " (" + canonico.medio().etiqueta()
								+ ", operación " + canonico.operacionCanonica() + ") quedó por revisar: "
								+ motivo.get().descripcion() + ". " + detalle + " No se aplicó a ninguna cuota."
								+ marcaSimulada(orden));
				return Resultado.POR_REVISAR;
			}
			TipoComprobante tipo = orden.getComprobanteTipo();
			DatosComprobante datos = tipo == TipoComprobante.FACTURA
					? new DatosComprobante(tipo, null, orden.getApoderado().getRuc(), orden.getApoderado().getRazonSocial())
					: new DatosComprobante(tipo, orden.getApoderado().getId(), null, null);
			Pago pago = registro.registrarEnLinea(canal, cuotas, new PedidoPagoEnLinea(orden.getFamilia().getId(),
					cuotas.stream().map(Cuota::getId).toList(), canonico.medio(), canonico.operacionCanonica(),
					canonico.monto(), false, datos, ordenId, claveDe(ordenId), fechaCanal, LocalDate.now(reloj),
					"Confirmado por la pasarela " + orden.getProveedor().name() + " (cargo " + canonico.cargoId() + ")."
							+ (tardia ? " PAGO TARDÍO: la orden ya había vencido." : "") + marcaSimulada(orden)));
			orden.marcarPagada();
			if (orden.getProveedor() == ProveedorPasarela.SIMULADA) {
				auditoria.registrar(AccionAuditoria.PASARELA_SIMULADA_USADA, "orden_pago", ordenId.toString(), null,
						"PAGADA", "PAGO SIMULADO · NO ES DINERO REAL: pago " + pago.getId() + " por "
								+ Dinero.formatear(pago.getTotal()) + " con la pasarela simulada.");
			}
			if (tardia) {
				auditoria.registrar(AccionAuditoria.PAGO_EN_LINEA_TARDIO, "orden_pago", ordenId.toString(), "VENCIDA",
						"PAGADA", "La pasarela confirmó el pago de una orden vencida y se aplicó: pago " + pago.getId()
								+ " por " + Dinero.formatear(pago.getTotal()) + ".");
			}
			return Resultado.PAGADA;
		});
	}

	private Resultado vencer(Long ordenId) {
		return transaccion.execute(e -> {
			OrdenPago orden = ordenes.bloquear(ordenId).orElseThrow();
			if (orden.getEstado() != EstadoOrden.CREADA) {
				return Resultado.SIN_CAMBIOS;
			}
			orden.vencer();
			auditoria.registrar(AccionAuditoria.ORDEN_PAGO_VENCIDA, "orden_pago", ordenId.toString(), "CREADA", "VENCIDA",
					"El pago en línea de " + orden.getFamilia().getNombre() + " por " + Dinero.formatear(orden.getMonto())
							+ " venció sin pagarse" + (orden.getProveedorOrdenId() == null
									? " (no se pudo crear en la pasarela)" : "") + ". Nada se cobró.");
			return Resultado.VENCIDA;
		});
	}

	private Resultado rechazar(Long ordenId) {
		return transaccion.execute(e -> {
			OrdenPago orden = ordenes.bloquear(ordenId).orElseThrow();
			if (orden.getEstado() != EstadoOrden.CREADA) {
				return Resultado.SIN_CAMBIOS;
			}
			orden.rechazar();
			auditoria.registrar(AccionAuditoria.ORDEN_PAGO_RECHAZADA, "orden_pago", ordenId.toString(), "CREADA",
					"RECHAZADA", "La pasarela no completó el pago en línea de " + orden.getFamilia().getNombre() + " por "
							+ Dinero.formatear(orden.getMonto()) + ". Nada se cobró.");
			return Resultado.RECHAZADA;
		});
	}

	/**
	 * El apoderado desconoció el cargo ante su banco: alerta CRÍTICA y una solicitud de anulación (devolución)
	 * prellenada para que Administración la revise. No se anula nada solo.
	 */
	private Resultado contracargo(Long ordenId) {
		return transaccion.execute(e -> {
			OrdenPago orden = ordenes.findById(ordenId).orElseThrow();
			Pago pago = registro.pagoDeOrden(ordenId).orElse(null);
			if (pago == null || !pago.vigente()) {
				return Resultado.SIN_CAMBIOS;
			}
			auditoria.registrar(AccionAuditoria.CONTRACARGO_RECIBIDO, "orden_pago", ordenId.toString(), "PAGADA",
					"CONTRACARGO", "La pasarela informa un contracargo del pago en línea " + pago.getComprobante()
							.numeroCompleto() + " de " + orden.getFamilia().getNombre() + " por "
							+ Dinero.formatear(pago.getTotal()) + ". Se pidió su anulación (devolución) para revisión.");
			try {
				anulaciones.solicitarPorContracargo(pago.getId(), "Contracargo de la pasarela " + orden.getProveedor().name()
						+ " del cargo " + orden.getCargoId() + ": el apoderado desconoció el pago ante su banco.");
			}
			catch (ReglaNegocioException yaPedida) {
				// Ya hay una solicitud pendiente para este pago: la alerta queda en la bitácora.
			}
			return Resultado.CONTRACARGO;
		});
	}

	private static String detalleNoAplicable(MotivoNoAplicable motivo, List<Cuota> cuotas, String operacion) {
		return switch (motivo) {
			case CUOTA_NO_COBRABLE -> "Cuotas: " + String.join("; ", cuotas.stream().map(c -> c.getDescripcion() + " de "
					+ c.getAlumno().nombreCompleto() + " (" + c.getEstado().name().toLowerCase(java.util.Locale.ROOT) + ")")
					.toList()) + ".";
			case MONTO_CAMBIO -> "El saldo de las cuotas cambió desde que se inició el pago.";
			case EXCESO -> "El monto supera lo que se debe.";
			case OPERACION_DUPLICADA -> "La operación " + operacion + " ya está registrada en otro pago vigente.";
		};
	}

	private static String marcaSimulada(OrdenPago orden) {
		return orden.getProveedor() == ProveedorPasarela.SIMULADA ? " PAGO SIMULADO · NO ES DINERO REAL." : "";
	}

	/** La clave del pago se deriva de la orden: el mismo aviso dos veces no puede crear dos pagos. */
	static UUID claveDe(Long ordenId) {
		return UUID.nameUUIDFromBytes(("orden_pago:" + ordenId).getBytes(StandardCharsets.UTF_8));
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	/** Lo que se necesita de la orden fuera de la transacción. */
	private record Instantanea(EstadoOrden estado, ProveedorPasarela proveedor, String proveedorOrdenId,
			LocalDateTime venceEn) {

		static Instantanea de(OrdenPago o) {
			return new Instantanea(o.getEstado(), o.getProveedor(), o.getProveedorOrdenId(), o.getVenceEn());
		}
	}
}
