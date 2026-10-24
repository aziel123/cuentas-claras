package pe.edu.virgenmaria.cuentasclaras.plataforma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

import java.util.Set;

/**
 * Sprint 8, tanda 1 (sección 3.3): el formulario del alta de un colegio. Los datos salen del contrato firmado (decisión
 * 137). El logo llega aparte, como archivo.
 * <p>
 * Sprint 11, tanda 1 (sección 3.7): sin el año escolar. El colegio nace sin año y lo configura su Promotoría o su Dirección
 * con su firma (primer paso de la Puesta en marcha); el operador nunca lo crea.
 */
public record AltaColegioRequest(
		@NotBlank(message = "Escribe el nombre comercial del colegio.")
		@Size(max = 150, message = "El nombre puede tener hasta 150 caracteres.") String nombre,
		@NotBlank(message = "Escribe la razón social.")
		@Size(max = 150, message = "La razón social puede tener hasta 150 caracteres.") String razonSocial,
		@NotBlank(message = "Escribe el RUC.")
		@Size(max = 20, message = "El RUC tiene 11 dígitos.") String ruc,
		@NotBlank(message = "Escribe la dirección fiscal.")
		@Size(max = 200, message = "La dirección puede tener hasta 200 caracteres.") String direccion,
		@NotBlank(message = "Escribe el correo institucional del colegio.")
		@Email(message = "Revisa el correo institucional: no parece válido.")
		@Size(max = 150, message = "El correo puede tener hasta 150 caracteres.") String correoInstitucional,
		@NotEmpty(message = "Elige al menos un nivel.") Set<Nivel> niveles,
		@Size(max = 30, message = "El código del contrato puede tener hasta 30 caracteres.") String contratoCodigo,
		@NotBlank(message = "Escribe el nombre completo del titular.")
		@Size(max = 150, message = "El nombre del titular puede tener hasta 150 caracteres.") String titularNombre,
		@NotBlank(message = "Escribe el DNI del titular.")
		@Pattern(regexp = "[0-9]{8}|[A-Z0-9]{9,12}", message = "El documento del titular: DNI de 8 dígitos o CE de 9 a 12.")
		String titularDocumento,
		@NotBlank(message = "Escribe el usuario con el que ingresará el titular.")
		@Size(max = 60, message = "El usuario puede tener hasta 60 caracteres.") String titularUsuario,
		@Size(max = 20, message = "Revisa el celular: escribe 9 dígitos que empiecen con 9.") String titularTelefono,
		@Email(message = "Revisa el correo del titular: no parece válido.")
		@Size(max = 150, message = "El correo puede tener hasta 150 caracteres.") String titularCorreo) {

	public static AltaColegioRequest vacio() {
		return new AltaColegioRequest("", "", "", "", "", Set.of(Nivel.INICIAL, Nivel.PRIMARIA, Nivel.SECUNDARIA), "", "",
				"", "", "", "");
	}
}
