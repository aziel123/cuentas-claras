package pe.edu.virgenmaria.cuentasclaras.academico.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;

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
 * Sprint 9, tanda 1 (sección 4): una asignación docente solo cambia su estado, su solicitud, su resolución y su fin. El
 * docente, la sección, el año, el tipo, las áreas y quién la propuso no cambian ni por la entidad ({@code updatable =
 * false}, sin setters) ni por SQL (GRANT por columna: 1143). Nunca se borra.
 */
class InmutabilidadAcademicoTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2027, 3, 1, 8, 0);

	private static String permisos() throws IOException {
		return Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));
	}

	@Test
	void lasColumnasActualizablesSonExactamenteLasDelGrant() throws IOException {
		assertThat(ColumnasActualizables.de(AsignacionDocente.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "asignacion_docente"))
				.doesNotContain("usuario_id", "seccion_id", "anio_escolar_id", "tipo", "areas", "propuesto_por",
						"colegio_id", "creado_por", "creado_en");
		assertThat(permisos()).doesNotContainPattern("GRANT [^;]*DELETE[^;]*ON cuentasclaras\\.asignacion_docente");
	}

	@Test
	void noTieneSettersYSoloCambiaPorSusTransiciones() {
		assertThat(Arrays.stream(AsignacionDocente.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
				.map(Method::getName)).noneMatch(n -> n.startsWith("set"));
		AsignacionDocente a = AsignacionDocente.propuesta(1L, 2L, 3L, TipoAsignacion.TUTOR, null, "administracion");
		assertThat(a.getEstado()).isEqualTo(EstadoAsignacion.PROPUESTA);
		assertThatThrownBy(() -> a.terminar("director", "Cambió de colegio", AHORA)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> a.aprobar("administracion", AHORA)).isInstanceOf(IllegalStateException.class);
		a.enlazarSolicitud(9L);
		assertThatThrownBy(() -> a.enlazarSolicitud(10L)).isInstanceOf(IllegalStateException.class);
		a.aprobar("director", AHORA);
		assertThat(a.esVigente()).isTrue();
		assertThatThrownBy(() -> a.rechazar("director", AHORA)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> a.terminar("director", "corto", AHORA)).isInstanceOf(ReglaNegocioException.class);
		a.terminar("director", "Cambió de colegio a mitad de año", AHORA);
		assertThat(a.getEstado()).isEqualTo(EstadoAsignacion.TERMINADA);
		assertThat(a.getResueltoPor()).as("quien la aprobó no cambia al terminarla").isEqualTo("director");
		assertThatThrownBy(() -> a.aprobar("otro", AHORA)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(a::impedirBorrado).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void elAuxiliarNoTieneAreasYLasAreasTienenTope() {
		assertThatThrownBy(() -> AsignacionDocente.propuesta(1L, 2L, 3L, TipoAsignacion.AUXILIAR, "Matemática", "x"))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> AsignacionDocente.propuesta(1L, 2L, 3L, TipoAsignacion.DOCENTE, "a".repeat(121), "x"))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(AsignacionDocente.propuesta(1L, 2L, 3L, TipoAsignacion.DOCENTE, "  Matemática  ", "x").getAreas())
				.isEqualTo("Matemática");
		assertThat(AsignacionDocente.propuesta(1L, 2L, 3L, TipoAsignacion.AUXILIAR, "  ", "x").getAreas()).isNull();
	}
}
