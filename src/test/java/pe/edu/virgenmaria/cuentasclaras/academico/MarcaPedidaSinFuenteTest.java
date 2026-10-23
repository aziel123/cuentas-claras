package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.MarcaPedida;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 2 (A15): lo que manda la hoja del día no tiene {@code fuente}: el marcador no está habilitado y nadie lo
 * puede pedir desde el formulario (la fuente la pone el servicio, siempre DOCENTE; los triggers rechazan MARCADOR).
 */
class MarcaPedidaSinFuenteTest {

	@Test
	void niLaMarcaNiLaHojaTienenFuente() {
		for (Class<?> c : new Class<?>[] { MarcaPedida.class, HojaRequest.class }) {
			assertThat(Arrays.stream(c.getRecordComponents()).map(RecordComponent::getName)
					.map(n -> n.toLowerCase(Locale.ROOT))).as(c.getSimpleName()).noneMatch(n -> n.contains("fuente"));
		}
	}

	@Test
	void elFormularioIgnoraUnaFuenteQueLeAgreguen() {
		HojaRequest hoja = HojaRequest.deFormulario(Map.of("estado-7", "FALTA", "fuente", "MARCADOR", "fuente-7",
				"MARCADOR"), null);
		assertThat(hoja.marcas()).singleElement().satisfies(m -> {
			assertThat(m.alumnoId()).isEqualTo(7L);
			assertThat(m.estado().name()).isEqualTo("FALTA");
		});
	}
}
