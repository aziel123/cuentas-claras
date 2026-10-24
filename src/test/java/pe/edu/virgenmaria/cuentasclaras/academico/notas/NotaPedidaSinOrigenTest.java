package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 2 (sección 3 del brief): lo que pide el docente para una celda NO trae el origen, el autor, el lote ni la
 * huella de la conclusión (los decide el servicio y la base los vuelve a exigir); el formulario se lee celda por celda.
 */
class NotaPedidaSinOrigenTest {

	@Test
	void notaPedidaSoloTraeLaCeldaElNivelYLaConclusion() {
		assertThat(Arrays.stream(NotaPedida.class.getRecordComponents()).map(RecordComponent::getName))
				.containsExactly("matriculaId", "competenciaId", "nivel", "conclusion");
	}

	@Test
	void elFormularioSeLeeCeldaPorCeldaEIgnoraLoDemas() {
		NotasRequest pedido = NotasRequest.deFormulario(Map.of("nivel-5-7", "B", "conclusion-5-7", "  Avanza con apoyo.  ",
				"nivel-6-7", "SIN", "nivel-8-7", "", "origen", "CORRECCION_APROBADA", "creado_por", "otro"), 3L);
		assertThat(pedido.solicitudId()).isEqualTo(3L);
		assertThat(pedido.notas()).containsExactlyInAnyOrder(new NotaPedida(5L, 7L, "B", "Avanza con apoyo."),
				new NotaPedida(6L, 7L, null, null));
		assertThatThrownBy(() -> NotasRequest.deFormulario(Map.of("nivel-5-7", "Z"), null))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> NotasRequest.deFormulario(Map.of("nivel-x-7", "A"), null))
				.isInstanceOf(ReglaNegocioException.class);
	}
}
