package pe.edu.virgenmaria.cuentasclaras.conectores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;

import java.util.List;

/**
 * Sprint 8, tanda 2: lo que escribe quien propone un conector (Administración o Promotoría). Los campos que no son del
 * tipo se ignoran; {@code PropuestaConector} valida los del tipo. El secreto (token, o llave secreta y secreto de los
 * avisos) se escribe ENTERO en cada propuesta: nadie lo vuelve a ver.
 */
public record PropuestaConectorRequest(
		@NotNull(message = "Elige el proveedor") ProveedorConector proveedor,
		@NotNull(message = "Elige el modo") ModoConector modo,
		@Size(max = 200, message = "La ruta tiene hasta 200 caracteres") String ruta,
		@Size(max = 4, message = "Una serie tiene 4 caracteres") String serieBoleta,
		@Size(max = 4, message = "Una serie tiene 4 caracteres") String serieFactura,
		@Size(max = 4, message = "Una serie tiene 4 caracteres") String serieNotaBoleta,
		@Size(max = 4, message = "Una serie tiene 4 caracteres") String serieNotaFactura,
		@Size(max = 12) String afectacionIgv,
		@Size(max = 120, message = "La llave pública tiene hasta 120 caracteres") String llavePublica,
		List<String> medios,
		@Size(max = 12) String montoMaximo,
		@Size(max = 30, message = "El número de cuenta tiene hasta 30 caracteres") String numeroCuenta,
		@Size(max = 30, message = "El CCI tiene 20 dígitos") String cci,
		@Size(max = 20) String modalidad,
		@Size(max = 60, message = "La glosa tiene hasta 60 caracteres") String patronAbono,
		@Size(max = 10) String abono,
		@Size(max = 400, message = "El secreto tiene hasta 400 caracteres") String secreto,
		@Size(max = 400, message = "El secreto de los avisos tiene hasta 400 caracteres") String secretoAviso,
		@NotBlank(message = "Escribe el motivo del cambio") @Size(min = 10, max = 500,
				message = "El motivo tiene entre 10 y 500 caracteres") String motivo) {

	@Override
	public String toString() {
		// Sin los secretos: este texto puede terminar en un log.
		return "PropuestaConectorRequest[proveedor=" + proveedor + ", modo=" + modo + "]";
	}
}
