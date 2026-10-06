package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroPagosAutomaticos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Contracargo de un pago en línea (correcciones del sprint 4, S4-A3 y QA-S4-1/2/3): el apoderado desconoció el cargo y
 * su banco ya le devolvió el dinero. Llegue por el aviso de la pasarela o por la línea CONTRACARGO de su liquidación, y
 * esté la orden PAGADA, APLICADA tras revisión, POR_REVISAR o DEVUELTA, siempre:
 * <ul>
 *   <li>se registra UNA vez en la orden (lo repetido no vuelve a alertar) y se audita resaltado (alerta CRÍTICA);</li>
 *   <li>si hay un pago vigente, se pide su anulación de tipo CONTRACARGO: SIN reembolso (el dinero ya volvió por el
 *       banco) y sin alerta de reembolso pendiente; la aprueba otra persona de Promotoría o Dirección;</li>
 *   <li>un ingreso por revisar ya no se puede aplicar ni devolver (también lo exige {@code trg_orden_pago_estado}).</li>
 * </ul>
 * Nada se anula solo. Lo usa {@code sistema.pasarela}, dentro de la transacción de quien llama.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
@PreAuthorize("hasRole('SISTEMA_PASARELA')")
public class ContracargosPasarela {

	public static final String POR_AVISO = "AVISO";

	public static final String POR_LIQUIDACION = "LIQUIDACION";

	private final OrdenPagoRepository ordenes;

	private final RegistroPagosAutomaticos registro;

	private final ServicioAnulacionPagos anulaciones;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public ContracargosPasarela(OrdenPagoRepository ordenes, RegistroPagosAutomaticos registro,
			ServicioAnulacionPagos anulaciones, AuditoriaService auditoria, Clock reloj) {
		this.ordenes = ordenes;
		this.registro = registro;
		this.anulaciones = anulaciones;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/**
	 * @param ordenId la orden cuyo cargo tuvo el contracargo (se bloquea)
	 * @param origen  {@link #POR_AVISO} o {@link #POR_LIQUIDACION}
	 * @return {@code true} si era nuevo; {@code false} si ya estaba registrado o la orden no se cobró
	 */
	public boolean registrar(Long ordenId, String origen, String detalle) {
		OrdenPago orden = ordenes.bloquear(ordenId).orElse(null);
		if (orden == null || orden.getCargoId() == null) {
			return false;
		}
		if (!orden.registrarContracargo(origen, LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS))) {
			return false;
		}
		ordenes.saveAndFlush(orden);
		Optional<Pago> pago = registro.pagoDeOrden(ordenId).filter(Pago::vigente);
		String monto = Dinero.formatear(orden.getMontoConfirmado() == null ? orden.getMonto() : orden.getMontoConfirmado());
		String que = switch (orden.getEstado()) {
			case PAGADA, APLICADA -> pago.map(p -> "Se pidió anular el pago " + p.getComprobante().numeroCompleto()
					+ " SIN reembolso (el banco ya devolvió el dinero al apoderado); lo aprueba Promotoría o Dirección.")
					.orElse("El pago ya estaba anulado.");
			case POR_REVISAR -> "El ingreso estaba por revisar: ya no se aplica a cuotas ni se devuelve.";
			case DEVUELTA -> "ATENCIÓN: este ingreso ya se había devuelto por la pasarela. Reclama a la pasarela el doble "
					+ "reembolso.";
			default -> "";
		};
		auditoria.registrar(AccionAuditoria.CONTRACARGO_RECIBIDO, "orden_pago", ordenId.toString(),
				orden.getEstado().name(), "CONTRACARGO", "La pasarela informa (" + (POR_LIQUIDACION.equals(origen)
						? "en su liquidación" : "por su aviso") + ") un contracargo del pago en línea de "
						+ orden.getFamilia().getNombre() + " por " + monto + " (cargo " + orden.getCargoId() + "). " + que
						+ (detalle == null ? "" : " " + detalle));
		if (pago.isPresent() && (orden.getEstado() == EstadoOrden.PAGADA || orden.getEstado() == EstadoOrden.APLICADA)) {
			try {
				anulaciones.solicitarPorContracargo(pago.get().getId(), "Contracargo de la pasarela "
						+ orden.getProveedor().name() + " del cargo " + orden.getCargoId() + ": el apoderado desconoció el "
						+ "pago ante su banco y ya recuperó su dinero.");
			}
			catch (ReglaNegocioException yaPedida) {
				// Ya hay una anulación pendiente de ese pago: la alerta y la bitácora quedan; si era una devolución, su
				// reembolso por la pasarela se rechaza (el cargo tiene contracargo).
			}
		}
		return true;
	}
}
