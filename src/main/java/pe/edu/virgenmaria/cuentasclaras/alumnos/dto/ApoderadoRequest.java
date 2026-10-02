package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;

/**
 * Apoderado nuevo o corrección de sus datos. Al corregir, {@code motivo} es obligatorio (lo exige el servicio):
 * cambiar el celular o el correo queda resaltado en la bitácora.
 */
public record ApoderadoRequest(
		@NotNull(message = "Elige el tipo de documento.") TipoDocumento tipoDocumento,
		@NotBlank(message = "Escribe el número de documento.") @Size(max = 20, message = "Revisa el número de documento.")
		String numeroDocumento,
		@NotBlank(message = "Escribe el apellido paterno.") @Size(max = 60, message = "Puede tener hasta 60 caracteres.")
		String apellidoPaterno,
		@Size(max = 60, message = "Puede tener hasta 60 caracteres.") String apellidoMaterno,
		@NotBlank(message = "Escribe los nombres.") @Size(max = 60, message = "Puede tener hasta 60 caracteres.")
		String nombres,
		@NotNull(message = "Elige el parentesco.") Parentesco parentesco,
		@Size(max = 30, message = "Revisa el celular.") String telefonoWhatsapp,
		@Size(max = 150, message = "El correo puede tener hasta 150 caracteres.") String correo,
		@Size(max = 500, message = "El motivo puede tener hasta 500 caracteres.") String motivo) {

	public static ApoderadoRequest vacio() {
		return new ApoderadoRequest(TipoDocumento.DNI, "", "", "", "", null, "", "", "");
	}
}
