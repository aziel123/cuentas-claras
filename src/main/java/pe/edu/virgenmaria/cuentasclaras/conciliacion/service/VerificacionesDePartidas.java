package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.service.RegistroVerificacionAutomatica;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoExtracto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.LiquidacionLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.TipoLineaLiquidacion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.LiquidacionLineaRepository;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.repository.LineaRecaudacionRepository;

/**
 * De una partida CONFIRMADA sobre un extracto CONFIRMADO salen las verificaciones AUTOMATICAS de lo que cubre (sección 3,
 * decisión 7 del diseño): el pago digital, el depósito, los pagos de la liquidación (por su bruto) o los pagos del lote
 * de recaudación, con la fecha del movimiento del banco. Lo hace {@code sistema.conciliacion} (lo exige
 * {@link RegistroVerificacionAutomatica}), dentro de la transacción de quien llama. Es idempotente.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class VerificacionesDePartidas {

	private final RegistroVerificacionAutomatica registro;

	private final LiquidacionLineaRepository lineasLiquidacion;

	private final LineaRecaudacionRepository lineasRecaudacion;

	private final PagoRepository pagos;

	public VerificacionesDePartidas(RegistroVerificacionAutomatica registro, LiquidacionLineaRepository lineasLiquidacion,
			LineaRecaudacionRepository lineasRecaudacion, PagoRepository pagos) {
		this.registro = registro;
		this.lineasLiquidacion = lineasLiquidacion;
		this.lineasRecaudacion = lineasRecaudacion;
		this.pagos = pagos;
	}

	/** @return cuántas verificaciones nuevas dejó */
	public int verificar(PartidaConciliacion partida) {
		MovimientoBancario m = partida.getMovimiento();
		if (partida.getEstado() != EstadoPartida.CONFIRMADA || m.getExtracto().getEstado() != EstadoExtracto.CONFIRMADO) {
			return 0;
		}
		int nuevas = 0;
		switch (partida.getObjetoTipo()) {
			// S4-C1: lo que se guarda como «visto en el banco» es el monto del MOVIMIENTO (el trigger exige que sea el del
			// movimiento y el del pago o depósito), nunca el del objeto.
			case PAGO -> nuevas += uno(registro.verificarPago(partida.getPagoId(), partida.getId(), m.getFecha(),
					m.getMonto(), m.getNumeroOperacion()));
			case DEPOSITO -> nuevas += uno(registro.verificarDeposito(partida.getDepositoId(), partida.getId(), m.getFecha(),
					m.getMonto(), m.getNumeroOperacion()));
			case LIQUIDACION -> {
				for (LiquidacionLinea l : lineasLiquidacion.findByLiquidacionIdOrderByNumeroAsc(partida.getLiquidacionId())) {
					if (l.getTipo() == TipoLineaLiquidacion.CARGO && l.getPagoId() != null) {
						nuevas += uno(registro.verificarPago(l.getPagoId(), partida.getId(), m.getFecha(), l.getBruto(), null));
					}
				}
			}
			case LOTE_RECAUDACION -> {
				var ids = lineasRecaudacion.findByLoteIdOrderByNumeroAsc(partida.getLoteRecaudacionId()).stream()
						.map(LineaRecaudacion::getId).toList();
				for (Pago p : ids.isEmpty() ? java.util.List.<Pago>of() : pagos.findByLineaRecaudacionIdIn(ids)) {
					if (p.vigente()) {
						nuevas += uno(registro.verificarPago(p.getId(), partida.getId(), m.getFecha(), p.getTotal(), null));
					}
				}
			}
			default -> {
				// Un reembolso o una explicación no dejan verificación.
			}
		}
		return nuevas;
	}

	private static int uno(boolean hecho) {
		return hecho ? 1 : 0;
	}
}
