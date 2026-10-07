package pe.edu.virgenmaria.cuentasclaras.familias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;

/** «¿Algo no cuadra?» desde el portal: qué pasó, el pago o la cuota (opcional, de la propia familia) y el texto. */
public record AvisoRequest(@NotNull(message = "Elige qué no cuadra.") TipoAvisoFamilia tipo, Long pagoId, Long cuotaId,
		@NotBlank(message = "Cuéntanos qué pasó.") @Size(max = 500, message = "Escribe hasta 500 caracteres.") String texto) {

	public static AvisoRequest vacio() {
		return new AvisoRequest(null, null, null, "");
	}
}
