package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;

import java.time.LocalDate;

/** Corrección de los datos del alumno, con motivo (queda en la bitácora). */
public record ActualizarAlumnoRequest(
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
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
