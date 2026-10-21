package pe.edu.virgenmaria.cuentasclaras.academico.dto.portal;

import jakarta.validation.constraints.NotNull;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Canal;
import pe.edu.virgenmaria.cuentasclaras.academico.model.Finalidad;

/** Sprint 9, tanda 2: el apoderado da o retira un consentimiento en el portal. */
public record ConsentimientoRequest(
		@NotNull(message = "Elige el aviso.") Finalidad finalidad,
		@NotNull(message = "Elige el canal.") Canal canal,
		boolean otorga) {
}
