package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.model.DepositoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.VerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.DepositoCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.VerificacionBancariaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Única entrada de la conciliación automática (sprint 4, tanda 3) a la verificación bancaria del sprint 3: cuando una
 * partida queda CONFIRMADA sobre un extracto CONFIRMADO a ciegas, {@code sistema.conciliacion} deja la verificación
 * AUTOMATICA de lo que cubre (un pago digital o un depósito). Así todo lo del sprint 3 que pregunta «¿está verificado?»
 * (anular un pago digital, las alertas, el muestreo) sigue funcionando sin cambios.
 * <p>
 * Corre dentro de la transacción de quien llama. Un pago o depósito ya verificado (a mano o por otra partida) no se
 * vuelve a verificar: la base admite una verificación por pago y una por depósito (UNIQUE).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
@PreAuthorize("hasRole('SISTEMA_CONCILIACION')")
public class RegistroVerificacionAutomatica {

	private final PagoRepository pagos;

	private final DepositoCajaRepository depositos;

	private final VerificacionBancariaRepository verificaciones;

	public RegistroVerificacionAutomatica(PagoRepository pagos, DepositoCajaRepository depositos,
			VerificacionBancariaRepository verificaciones) {
		this.pagos = pagos;
		this.depositos = depositos;
		this.verificaciones = verificaciones;
	}

	/**
	 * @return {@code true} si la dejó; {@code false} si el pago ya estaba verificado, está anulado, no es digital o el
	 *         monto visto en el banco no es el suyo
	 */
	public boolean verificarPago(Long pagoId, Long partidaId, LocalDate fechaBanco, BigDecimal montoBanco,
			String operacionBanco) {
		Pago pago = pagos.findById(pagoId).orElse(null);
		// S4-C1: lo que se vio en el banco es por el mismo monto del pago; si no, no hay verificación (también lo exige
		// el trigger, que compara con el movimiento del extracto).
		if (pago == null || !pago.vigente() || !pago.getMedio().digital() || verificaciones.existsByPagoId(pagoId)
				|| montoBanco == null || montoBanco.compareTo(pago.getTotal()) != 0) {
			return false;
		}
		verificaciones.save(VerificacionBancaria.automaticaDePago(pago, partidaId, fechaBanco, montoBanco, operacionBanco,
				actor()));
		return true;
	}

	/** @return {@code true} si la dejó; {@code false} si el depósito ya estaba verificado */
	public boolean verificarDeposito(Long depositoId, Long partidaId, LocalDate fechaBanco, BigDecimal montoBanco,
			String operacionBanco) {
		DepositoCaja deposito = depositos.findById(depositoId).orElse(null);
		if (deposito == null || verificaciones.existsByDepositoId(depositoId) || montoBanco == null
				|| montoBanco.compareTo(deposito.getMonto()) != 0) {
			return false;
		}
		verificaciones.save(VerificacionBancaria.automaticaDeDeposito(deposito, partidaId, fechaBanco, montoBanco,
				operacionBanco, actor()));
		return true;
	}

	private static String actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? null : autenticacion.getName();
	}
}
