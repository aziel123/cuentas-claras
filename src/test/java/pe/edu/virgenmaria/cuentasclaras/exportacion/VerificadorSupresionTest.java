package pe.edu.virgenmaria.cuentasclaras.exportacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.LectorTablas;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.TablasExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.VerificadorSupresion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sprint 11, tanda 2 (AP2-3 y AP2-11): al arrancar en prod, la aplicación se NIEGA si un acta ejecutada fuera de la base
 * ({@code CC_DIR_ACTAS}) no está aplicada (un respaldo restaurado sin {@code reaplicar.sh}) o si un colegio SUPRIMIDO
 * tiene filas en las tablas que se suprimen. En dev solo avisa.
 */
class VerificadorSupresionTest {

	@TempDir
	Path actas;

	private final ColegioRepository colegios = mock(ColegioRepository.class);

	private final LectorTablas lector = mock(LectorTablas.class);

	@Test
	void unActaExternaSinAplicarNoArrancaEnProd() throws Exception {
		Files.writeString(actas.resolve("acta-7-12.json"), "{\"id\": 12, \"colegio_id\": 7}");
		when(colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO)).thenReturn(List.of(colegio(7L, EstadoColegio.CERRADO,
				null)));
		VerificadorSupresion prod = verificador("prod");
		assertThat(prod.problemas()).singleElement().asString().contains("acta de supresión 12").contains("reaplicar.sh");
		assertThatThrownBy(prod::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
		// En dev, solo el aviso.
		verificador("dev").afterPropertiesSet();
	}

	@Test
	void unColegioSuprimidoConFilasNoArranca() {
		when(colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO)).thenReturn(List.of(colegio(7L, EstadoColegio.SUPRIMIDO,
				12L)));
		when(lector.contar(any(), anyLong())).thenReturn(0L);
		when(lector.contar(argThat((TablasExportacion.Tabla t) -> t != null && t.nombre().equals("alumno")), anyLong()))
				.thenReturn(3L);
		assertThat(verificador("prod").problemas()).singleElement().asString().contains("3 filas en alumno");
	}

	@Test
	void conTodoEnOrdenArranca() throws Exception {
		Files.writeString(actas.resolve("acta-7-12.json"), "{}");
		when(colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO)).thenReturn(List.of(colegio(7L, EstadoColegio.SUPRIMIDO,
				12L)));
		when(lector.contar(any(), anyLong())).thenReturn(0L);
		VerificadorSupresion prod = verificador("prod");
		assertThat(prod.problemas()).isEmpty();
		prod.afterPropertiesSet();
	}

	private VerificadorSupresion verificador(String perfil) {
		MockEnvironment entorno = new MockEnvironment();
		entorno.setActiveProfiles(perfil);
		return new VerificadorSupresion(colegios, lector, entorno, actas.toString());
	}

	private static Colegio colegio(Long id, EstadoColegio estado, Long acta) {
		Colegio c = new Colegio("Colegio " + id);
		ReflectionTestUtils.setField(c, "id", id);
		ReflectionTestUtils.setField(c, "estado", estado);
		ReflectionTestUtils.setField(c, "actaSupresionId", acta);
		return c;
	}
}
