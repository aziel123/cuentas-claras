package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;

/**
 * Sprint 10, tanda 1: lo que Dirección elige para el año. Los rangos los vuelven a validar la entidad y la base (CHECK de
 * V35). Sin ningún campo de deuda, pago ni bloqueo (Ley 29571).
 */
public record ConfiguracionEvaluacionRequest(
		@NotNull(message = "Elige el año.") Long anioId,
		@NotNull(message = "Elige la versión del currículo.") Long curriculoVersionId,
		@NotNull(message = "Elige cómo se divide el año.") TipoPeriodo tipoPeriodo,
		@NotNull(message = "Escribe el largo máximo de la conclusión.")
		@Min(value = 100, message = "De 100 a 1000 caracteres.") @Max(value = 1000, message = "De 100 a 1000 caracteres.")
		Integer conclusionMax,
		Boolean conclusionSiempre,
		@NotNull(message = "Escribe los días de cierre anticipado.")
		@Min(value = 0, message = "De 0 a 15 días.") @Max(value = 15, message = "De 0 a 15 días.") Integer diasCierreAnticipado,
		@NotBlank(message = "Escribe la lengua materna.") @Size(min = 3, max = 30, message = "De 3 a 30 caracteres.")
		String lenguaMaterna,
		@Size(max = 80, message = "Hasta 80 caracteres.") String dre,
		@Size(max = 80, message = "Hasta 80 caracteres.") String ugel) {

	public static ConfiguracionEvaluacionRequest de(ConfiguracionEvaluacionVista v) {
		return new ConfiguracionEvaluacionRequest(v.anioId(), v.curriculoVersionId(), v.tipoPeriodo(), v.conclusionMax(),
				v.conclusionSiempre(), v.diasCierreAnticipado(), v.lenguaMaterna(), v.dre(), v.ugel());
	}

	public ConfiguracionEvaluacion.Valores valores() {
		return new ConfiguracionEvaluacion.Valores(curriculoVersionId, tipoPeriodo, conclusionMax == null ? 0 : conclusionMax,
				Boolean.TRUE.equals(conclusionSiempre), diasCierreAnticipado == null ? -1 : diasCierreAnticipado, lenguaMaterna,
				dre, ugel);
	}
}
