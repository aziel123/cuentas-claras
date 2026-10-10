package pe.edu.virgenmaria.cuentasclaras.familias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.familias.model.DerechoDatos;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;

/**
 * «¿Algo no cuadra?» desde el portal: qué pasó, el pago o la cuota (opcional, de la propia familia), el texto y, si es un
 * pedido sobre los datos personales (sprint 7, tanda 3), qué derecho ejerce la familia.
 */
public record AvisoRequest(@NotNull(message = "Elige qué no cuadra.") TipoAvisoFamilia tipo, Long pagoId, Long cuotaId,
		DerechoDatos derecho,
		@NotBlank(message = "Cuéntanos qué pasó.") @Size(max = 500, message = "Escribe hasta 500 caracteres.") String texto) {

	/** Un aviso que no es sobre datos personales (sin derecho). */
	public AvisoRequest(TipoAvisoFamilia tipo, Long pagoId, Long cuotaId, String texto) {
		this(tipo, pagoId, cuotaId, null, texto);
	}

	public static AvisoRequest vacio() {
		return new AvisoRequest(null, null, null, null, "");
	}
}
