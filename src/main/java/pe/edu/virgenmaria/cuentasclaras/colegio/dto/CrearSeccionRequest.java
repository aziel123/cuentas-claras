package pe.edu.virgenmaria.cuentasclaras.colegio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

/** Sección nueva de un grado en un año. */
public record CrearSeccionRequest(
		@NotNull(message = "Elige el grado.") Grado grado,
		@NotBlank(message = "Escribe el nombre de la sección, por ejemplo A.")
		@Size(max = 30, message = "El nombre puede tener hasta 30 caracteres.") String nombre) {

	public static CrearSeccionRequest vacio() {
		return new CrearSeccionRequest(null, "");
	}
}
