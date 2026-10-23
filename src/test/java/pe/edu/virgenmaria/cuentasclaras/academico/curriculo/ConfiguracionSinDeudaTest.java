package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto.ConfiguracionEvaluacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 1 (E28; Ley 29571 e INDECOPI): la evaluación no se condiciona al pago. La configuración del año, su
 * formulario y los periodos no tienen (por reflexión) ningún campo de deuda, pago, mora, pensión, cuota ni de bloqueo o
 * retención: no existe la opción de «retener por deuda».
 */
class ConfiguracionSinDeudaTest {

	private static final Pattern PROHIBIDO = Pattern.compile("deud|pag|mor[oa]|pension|cuota|bloque|reten|saldo|cobr");

	@Test
	void niLaConfiguracionNiLosPeriodosTienenCamposDeDeudaPagoOBloqueo() {
		List<String> nombres = new ArrayList<>();
		for (Class<?> clase : new Class<?>[] { ConfiguracionEvaluacion.class, PeriodoEvaluacion.class }) {
			Arrays.stream(clase.getDeclaredFields()).map(Field::getName).forEach(nombres::add);
		}
		for (Class<?> registro : new Class<?>[] { ConfiguracionEvaluacionRequest.class, ConfiguracionEvaluacion.Valores.class }) {
			Arrays.stream(registro.getRecordComponents()).map(RecordComponent::getName).forEach(nombres::add);
		}
		assertThat(nombres).isNotEmpty().noneMatch(n -> PROHIBIDO.matcher(n.toLowerCase(Locale.ROOT)).find());
	}
}
