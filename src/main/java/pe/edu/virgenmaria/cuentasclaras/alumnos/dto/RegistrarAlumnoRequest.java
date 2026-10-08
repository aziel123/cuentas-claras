package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;

import java.time.LocalDate;

/**
 * Formulario de alumno nuevo. El responsable de pago es UNO de estos:
 * <ul>
 *   <li>un apoderado ya registrado: {@code apoderadoExistenteId} (desde la familia) o
 *       {@code documentoApoderadoExistente} (su número de documento), por ejemplo para un hermano;</li>
 *   <li>un apoderado nuevo: los campos {@code apoderado*}. Se crea también su familia.</li>
 * </ul>
 * Si se elige una sección, el alumno queda matriculado (fecha por defecto: hoy o el inicio de clases).
 * Las reglas de cada dato las aplica {@code ReglasDatosPersonales} en el servicio.
 */
public record RegistrarAlumnoRequest(
		@NotNull(message = "Elige el tipo de documento.") TipoDocumento tipoDocumento,
		@NotBlank(message = "Escribe el número de documento.") @Size(max = 20, message = "Revisa el número de documento.")
		String numeroDocumento,
		@NotBlank(message = "Escribe el apellido paterno.") @Size(max = 60, message = "Puede tener hasta 60 caracteres.")
		String apellidoPaterno,
		@Size(max = 60, message = "Puede tener hasta 60 caracteres.") String apellidoMaterno,
		@NotBlank(message = "Escribe los nombres.") @Size(max = 60, message = "Puede tener hasta 60 caracteres.")
		String nombres,
		@NotNull(message = "Elige la fecha de nacimiento.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate fechaNacimiento,
		Long apoderadoExistenteId,
		@Size(max = 20, message = "Revisa el número de documento.") String documentoApoderadoExistente,
		TipoDocumento apoderadoTipoDocumento,
		@Size(max = 20, message = "Revisa el número de documento.") String apoderadoNumeroDocumento,
		@Size(max = 60, message = "Puede tener hasta 60 caracteres.") String apoderadoApellidoPaterno,
		@Size(max = 60, message = "Puede tener hasta 60 caracteres.") String apoderadoApellidoMaterno,
		@Size(max = 60, message = "Puede tener hasta 60 caracteres.") String apoderadoNombres,
		Parentesco apoderadoParentesco,
		@Size(max = 30, message = "Revisa el celular.") String apoderadoTelefonoWhatsapp,
		@Size(max = 150, message = "El correo puede tener hasta 150 caracteres.") String apoderadoCorreo,
		Long seccionId,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaMatricula) {

	public static RegistrarAlumnoRequest vacio(Long apoderadoExistenteId) {
		return new RegistrarAlumnoRequest(TipoDocumento.DNI, "", "", "", "", null, apoderadoExistenteId, "",
				TipoDocumento.DNI, "", "", "", "", null, "", "", null, null);
	}

	/** {@code true} si se escribió algún dato de un apoderado nuevo. */
	public boolean conApoderadoNuevo() {
		return lleno(apoderadoNumeroDocumento) || lleno(apoderadoApellidoPaterno) || lleno(apoderadoApellidoMaterno)
				|| lleno(apoderadoNombres) || lleno(apoderadoTelefonoWhatsapp) || lleno(apoderadoCorreo)
				|| apoderadoParentesco != null;
	}

	/** {@code true} si se eligió un apoderado ya registrado. */
	public boolean conApoderadoExistente() {
		return apoderadoExistenteId != null || lleno(documentoApoderadoExistente);
	}

	private static boolean lleno(String texto) {
		return texto != null && !texto.isBlank();
	}
}
