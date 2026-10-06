package pe.edu.virgenmaria.cuentasclaras.recaudacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedir devolver una línea de recaudación por revisar (correcciones del sprint 4, S4-A4): a QUÉ cuenta se devuelve
 * (banco, número y titular, los de quien pagó), para que quien aprueba lo vea en la bandeja y la conciliación espere
 * ver salir ese dinero del banco.
 */
public record DevolucionLineaRequest(
		@NotBlank(message = "Escribe el banco de destino.") @Size(max = 20, message = "El banco tiene hasta 20 caracteres.") String banco,
		@NotBlank(message = "Escribe la cuenta de destino.") @Size(max = 30, message = "La cuenta tiene hasta 30 caracteres.") String cuenta,
		@NotBlank(message = "Escribe el titular de la cuenta de destino.") @Size(max = 120, message = "El titular tiene hasta 120 caracteres.") String titular,
		@NotNull(message = "Escribe el motivo.") @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
