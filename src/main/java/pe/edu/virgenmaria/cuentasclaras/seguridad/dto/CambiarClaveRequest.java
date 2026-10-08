package pe.edu.virgenmaria.cuentasclaras.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Formulario de cambio de clave. La política completa la aplica {@code PoliticaClaves} en el servicio.
 */
public record CambiarClaveRequest(
		@NotBlank(message = "Escribe tu clave actual.") String claveActual,
		@NotBlank(message = "Escribe tu nueva clave.")
		@Size(min = 10, max = 64, message = "Tu nueva clave debe tener entre 10 y 64 caracteres.") String claveNueva,
		@NotBlank(message = "Repite tu nueva clave.") String confirmacion) {

	public static CambiarClaveRequest vacio() {
		return new CambiarClaveRequest("", "", "");
	}

	@Override
	public String toString() {
		// Nunca imprimir las claves.
		return "CambiarClaveRequest[***]";
	}
}
