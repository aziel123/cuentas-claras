package pe.edu.virgenmaria.cuentasclaras.matricula.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;
import pe.edu.virgenmaria.cuentasclaras.familias.model.AvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 5, tanda 2: la renovación no cambia su alumno, su familia, su año ni su matrícula de origen; el aviso de la
 * familia no cambia su texto, su tipo ni sus referencias. Ni por la entidad ({@code updatable = false}, sin setters) ni
 * por SQL (GRANT por columna: 1143). Las listas coinciden EXACTAMENTE con 02-permisos-tablas.sql.
 */
class InmutabilidadMatriculaTest {

	private static String permisos() throws IOException {
		return Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));
	}

	@Test
	void lasColumnasActualizablesDeLaRenovacionSonExactamenteLasDelGrant() throws IOException {
		assertThat(ColumnasActualizables.de(RenovacionMatricula.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "renovacion_matricula"))
				.doesNotContain("alumno_id", "familia_id", "anio_destino_id", "matricula_origen_id", "deuda_al_proponer",
						"vence_en");
	}

	@Test
	void lasColumnasActualizablesDelAvisoSonExactamenteLasDelGrant() throws IOException {
		assertThat(ColumnasActualizables.de(AvisoFamilia.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "aviso_familia"))
				.doesNotContain("texto", "tipo", "familia_id", "apoderado_id", "pago_id", "cuota_id");
	}

	@Test
	void nadaSeBorraYLaMatriculaMantieneSuGrantPorTabla() throws IOException {
		assertThat(permisos())
				.doesNotContainPattern("GRANT [^;]*DELETE[^;]*ON cuentasclaras\\.(renovacion_matricula|aviso_familia|matricula)")
				.contains("GRANT INSERT, UPDATE ON cuentasclaras.matricula TO 'cc_app'@'%';");
		// El alumno y el año de la matrícula no se cambian desde la entidad (y en MySQL, trg_matricula_estado).
		assertThat(ColumnasActualizables.de(Matricula.class)).doesNotContain("alumno_id", "anio_escolar_id");
	}

	@Test
	void sinSettersYUnaSolaRespuesta() {
		for (Class<?> clase : new Class<?>[] { RenovacionMatricula.class, AvisoFamilia.class }) {
			assertThat(Arrays.stream(clase.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
					.map(Method::getName)).as(clase.getSimpleName()).noneMatch(n -> n.startsWith("set"));
		}
		AvisoFamilia aviso = AvisoFamilia.nuevo(1L, 2L, TipoAvisoFamilia.OTRO, null, null, "Una consulta de la familia");
		LocalDateTime ahora = LocalDateTime.of(2026, 10, 2, 10, 0);
		aviso.atender("Ya lo revisamos con caja", "promotor", ahora);
		assertThatThrownBy(() -> aviso.atender("Otra respuesta", "director", ahora))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> AvisoFamilia.nuevo(1L, 2L, TipoAvisoFamilia.OTRO, null, null, "x".repeat(501)))
				.isInstanceOf(ReglaNegocioException.class);
	}
}
