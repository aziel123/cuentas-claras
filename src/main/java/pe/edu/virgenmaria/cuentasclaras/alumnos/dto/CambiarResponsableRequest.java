package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Nuevo responsable de pago: un apoderado de la familia ({@code apoderadoId}) o, por su número de documento, uno de
 * otra familia (el alumno pasa a esa familia). El motivo queda en la bitácora y el cambio se resalta.
 */
public record CambiarResponsableRequest(Long apoderadoId,
		@Size(max = 20, message = "Revisa el número de documento.") String documentoApoderado,
		@NotBlank(message = "Escribe el motivo.")
		@Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.") String motivo) {
}
