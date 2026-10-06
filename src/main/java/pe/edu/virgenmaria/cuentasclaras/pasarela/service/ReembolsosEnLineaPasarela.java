package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ReembolsosEnLinea;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;

import java.math.BigDecimal;

/**
 * El reembolso de un pago en línea por la API de su pasarela (S4-A3): al mismo cargo y al mismo medio de origen; nunca
 * a una cuenta que alguien escribe. Un cargo con contracargo no se reembolsa (el banco ya devolvió el dinero). Corre en
 * la transacción de quien llama (Administración, ya autorizada por {@code ServicioVerificacionBancaria}).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ReembolsosEnLineaPasarela implements ReembolsosEnLinea {

	private final OrdenPagoRepository ordenes;

	private final Pasarelas pasarelas;

	public ReembolsosEnLineaPasarela(OrdenPagoRepository ordenes, Pasarelas pasarelas) {
		this.ordenes = ordenes;
		this.pasarelas = pasarelas;
	}

	@Override
	public Reembolsado reembolsar(Long ordenPagoId, BigDecimal monto, String motivo) {
		OrdenPago orden = ordenes.bloquear(ordenPagoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pago en línea no encontrado"));
		orden.exigirSinContracargo();
		if (orden.getCargoId() == null) {
			throw new ReglaNegocioException("Este pago en línea no tiene un cargo de la pasarela que reembolsar.");
		}
		ReembolsoPasarela hecho = pasarelas.de(orden.getProveedor()).reembolsar(orden.getCargoId(), monto, motivo);
		if (!orden.getCargoId().equals(hecho.cargoId()) || hecho.monto().compareTo(monto) != 0) {
			throw new IllegalStateException("La pasarela reembolsó otro cargo u otro monto");
		}
		return new Reembolsado(hecho.cargoId(), hecho.reembolsoId(), hecho.monto());
	}
}
