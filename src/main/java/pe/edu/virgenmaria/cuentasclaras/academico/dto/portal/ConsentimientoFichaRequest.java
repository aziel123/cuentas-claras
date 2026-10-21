package pe.edu.virgenmaria.cuentasclaras.academico.dto.portal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;

/** Sprint 9, tanda 2 (E38): Administración registra el consentimiento de la ficha de matrícula firmada. */
public record ConsentimientoFichaRequest(
		@NotNull(message = "Elige al apoderado.") Long apoderadoId,
		@NotNull(message = "Elige el aviso.") Finalidad finalidad,
		@NotNull(message = "Elige el canal.") Canal canal,
		@NotBlank(message = "Escribe el código de la ficha firmada.")
		@Size(min = 3, max = 40, message = "El código de la ficha tiene de 3 a 40 caracteres.") String codigoFicha) {
}
