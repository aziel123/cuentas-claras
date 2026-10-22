package pe.edu.virgenmaria.cuentasclaras.comunicados.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;

/**
 * Sprint 9, tanda 3 (sección 3.8): lo que escribe quien envía un comunicado. El emisor se elige entre los roles REALES de
 * su cuenta (el servicio lo comprueba) y la etiqueta la arma el sistema; nunca hay un campo «firma». Texto plano.
 */
public record BorradorComunicado(
		@NotNull(message = "Elige quién firma el comunicado.") EmisorRol emisor,
		@NotNull(message = "Elige a quién va el comunicado.") AlcanceComunicado alcance,
		Nivel nivel,
		Grado grado,
		Long seccionId,
		@NotBlank(message = "Escribe el título.") @Size(min = 5, max = 120, message = "El título va de 5 a 120 caracteres.") String titulo,
		@NotBlank(message = "Escribe el texto.") @Size(min = 10, max = 4000, message = "El texto va de 10 a 4,000 caracteres.") String cuerpo,
		boolean urgente,
		boolean requiereConfirmacion) {
}
