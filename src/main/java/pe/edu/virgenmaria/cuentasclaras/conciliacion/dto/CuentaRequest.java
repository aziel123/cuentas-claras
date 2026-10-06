package pe.edu.virgenmaria.cuentasclaras.conciliacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.BancoCuenta;

/** Una cuenta del colegio para conciliar: banco, número (como aparece en el banco) y un nombre corto. */
public record CuentaRequest(
		@NotNull(message = "Elige el banco.") BancoCuenta banco,
		@NotBlank(message = "Escribe el número de la cuenta.") @Size(max = 40, message = "El número es demasiado largo.")
		String numero,
		@NotBlank(message = "Ponle un nombre corto a la cuenta.") @Size(max = 60, message = "El nombre es demasiado largo.")
		String alias) {
}
