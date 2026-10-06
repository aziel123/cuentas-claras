package pe.edu.virgenmaria.cuentasclaras.pasarela.model;

import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lo que la pasarela confirmó al CONSULTARLA con la llave secreta (nunca lo que dice un aviso): el cargo, su operación
 * en forma canónica, el monto, la moneda, el medio y cuándo se pagó (hora de Lima).
 */
public record CobroConfirmado(String cargoId, String operacionCanonica, BigDecimal monto, String moneda, MedioPago medio,
		LocalDateTime pagadoEn) {

	public CobroConfirmado {
		Objects.requireNonNull(cargoId, "cargoId");
		Objects.requireNonNull(operacionCanonica, "operacionCanonica");
		Objects.requireNonNull(monto, "monto");
		Objects.requireNonNull(moneda, "moneda");
		Objects.requireNonNull(medio, "medio");
		Objects.requireNonNull(pagadoEn, "pagadoEn");
	}
}
