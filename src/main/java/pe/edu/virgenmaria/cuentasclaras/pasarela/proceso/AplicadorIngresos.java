package pe.edu.virgenmaria.cuentasclaras.pasarela.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import pe.edu.virgenmaria.cuentasclaras.caja.service.DatosComprobante;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos.MotivoNoAplicable;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos.PedidoPagoEnLinea;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.service.IngresoAprobado;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registra, como {@code sistema.pasarela} y después del commit de la aprobación, el pago de un ingreso por revisar que
 * Promotoría o Dirección aprobaron aplicar a otras cuotas (el trigger del pago exige esa solicitud APROBADA). Si al
 * aplicarlo algo cambió (una cuota se pagó en el intertanto), no aplica nada: la orden sigue por revisar y se reintenta
 * en la siguiente pasada de {@link ConsultaOrdenesAbiertas}.
 */
@Component
public class AplicadorIngresos {

	private static final Logger LOG = LoggerFactory.getLogger(AplicadorIngresos.class);

	private final OrdenPagoRepository ordenes;

	private final SolicitudCambioRepository solicitudes;

	private final RegistroPagosAutomaticos registro;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AplicadorIngresos(OrdenPagoRepository ordenes, SolicitudCambioRepository solicitudes,
			RegistroPagosAutomaticos registro, AuditoriaService auditoria, PlatformTransactionManager transacciones,
			Clock reloj) {
		this.ordenes = ordenes;
		this.solicitudes = solicitudes;
		this.registro = registro;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alAprobarse(IngresoAprobado evento) {
		try {
			aplicar(evento.colegioId(), evento.ordenId(), evento.solicitudId());
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo aplicar el ingreso de la orden {}: {} (se reintentará)", evento.ordenId(),
					e.getClass().getSimpleName());
		}
	}

	/** @return el pago registrado, o vacío si no se pudo aplicar (la orden sigue por revisar) */
	public Optional<Long> aplicar(long colegioId, Long ordenId, Long solicitudId) {
		return EjecucionComoSistema.como(ActorSistema.PASARELA, colegioId, () -> aplicarEnContexto(ordenId, solicitudId));
	}

	/** Aplica las órdenes por revisar que ya tienen su aplicación aprobada (con el actor y el colegio ya fijados). */
	void aplicarPendientes() {
		for (OrdenPago orden : ordenes.findByEstadoInOrderByIdDesc(java.util.EnumSet.of(EstadoOrden.POR_REVISAR))) {
			solicitudes.findFirstByTipoAndEntidadAndEntidadIdAndEstadoOrderByIdDesc(TipoSolicitud.APLICAR_INGRESO,
					"orden_pago", orden.getId(), EstadoSolicitud.APROBADA)
					.ifPresent(s -> aplicarEnContexto(orden.getId(), s.getId()));
		}
	}

	private Optional<Long> aplicarEnContexto(Long ordenId, Long solicitudId) {
		LocalDate hoy = LocalDate.now(reloj);
		registro.prepararCanal(CanalCaja.PASARELA, hoy);
		return transaccion.execute(e -> {
			OrdenPago orden = ordenes.bloquear(ordenId).orElseThrow();
			if (orden.getEstado() != EstadoOrden.POR_REVISAR || orden.tieneContracargo()) {
				return Optional.<Long>empty();
			}
			SolicitudCambio solicitud = solicitudes.findById(solicitudId)
					.filter(s -> s.getTipo() == TipoSolicitud.APLICAR_INGRESO && s.getEstado() == EstadoSolicitud.APROBADA
							&& "orden_pago".equals(s.getEntidad()) && ordenId.equals(s.getEntidadId()))
					.orElseThrow(() -> new IllegalStateException("La aplicación no está aprobada"));
			Map<String, String> datos = DatosSolicitud.leer(solicitud.getDatos());
			Long destino = Long.valueOf(datos.get("familiaId"));
			List<Long> ids = java.util.Arrays.stream(datos.getOrDefault("cuotas", "").split(","))
					.filter(t -> !t.isBlank()).map(Long::valueOf).sorted().toList();
			CajaDiaria canal = registro.bloquearCanal(CanalCaja.PASARELA, hoy);
			List<Cuota> cuotas = registro.bloquearCuotas(ids);
			BigDecimal monto = orden.getMontoConfirmado();
			Optional<MotivoNoAplicable> motivo = registro.evaluar(destino, cuotas, monto, orden.getOperacion(), null);
			if (motivo.isPresent()) {
				LOG.warn("El ingreso de la orden {} no se pudo aplicar ({}): sigue por revisar.", ordenId, motivo.get());
				return Optional.<Long>empty();
			}
			BigDecimal debe = Dinero.sumar(cuotas.stream().map(Cuota::saldo).toList());
			boolean aCuenta = debe.compareTo(monto) > 0;
			boolean otraFamilia = !destino.equals(orden.getFamilia().getId());
			Pago pago = registro.registrarEnLinea(canal, cuotas, new PedidoPagoEnLinea(destino, ids,
					orden.getMedioConfirmado(), orden.getOperacion(), monto, aCuenta,
					new DatosComprobante(TipoComprobante.BOLETA, null, null, null), ordenId,
					ProcesadorPagosEnLinea.claveDe(ordenId), hoy, hoy, "Ingreso por revisar aplicado con la solicitud "
							+ solicitudId + " aprobada por " + solicitud.getResueltoPor()
							+ (otraFamilia ? ": el dinero pasa a OTRA familia." : ".")));
			orden.marcarAplicada();
			auditoria.registrar(AccionAuditoria.INGRESO_APLICADO, "orden_pago", ordenId.toString(), "POR_REVISAR",
					"APLICADA", "Se aplicó el ingreso de " + orden.getFamilia().getNombre() + " por "
							+ Dinero.formatear(monto) + " con el pago " + pago.getId() + " ("
							+ pago.getComprobante().numeroCompleto() + ")" + (aCuenta ? ", a cuenta" : "")
							+ (otraFamilia ? ", a OTRA familia" : "") + ". Pedido por " + solicitud.getSolicitadoPor()
							+ ", aprobado por " + solicitud.getResueltoPor() + ".");
			return Optional.of(pago.getId());
		});
	}
}
