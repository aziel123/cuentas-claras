package pe.edu.virgenmaria.cuentasclaras.academico.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.AreaCurricular;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.Competencia;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.ConfiguracionEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.CurriculoVersion;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EscalaLogro;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.EstadoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.PeriodoEvaluacion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 9, tanda 1 (sección 4): una asignación docente solo cambia su estado, su solicitud, su resolución y su fin. El
 * docente, la sección, el año, el tipo, las áreas y quién la propuso no cambian ni por la entidad ({@code updatable =
 * false}, sin setters) ni por SQL (GRANT por columna: 1143). Nunca se borra.
 * <p>
 * Sprint 10, tanda 1: el catálogo nacional es de solo lectura ({@code @Immutable}, todas sus columnas
 * {@code updatable = false}, sin setters y sin ningún GRANT de escritura); las áreas de una asignación son de solo inserción;
 * la configuración de evaluación y los periodos cambian solo las columnas de su GRANT (sin el colegio, el año, el número ni
 * quién los creó). Nada se borra.
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

	@Test
	void elCatalogoEsDeSoloLecturaYNoTieneGrantDeEscritura() throws IOException {
		for (Class<?> entidad : new Class<?>[] { CurriculoVersion.class, AreaCurricular.class, Competencia.class,
				EscalaLogro.class }) {
			assertThat(entidad.isAnnotationPresent(Immutable.class)).as(entidad.getSimpleName()).isTrue();
			for (Field campo : entidad.getDeclaredFields()) {
				if (Modifier.isStatic(campo.getModifiers()) || campo.isAnnotationPresent(Id.class)) {
					continue;
				}
				assertThat(campo.getAnnotation(Column.class)).as(entidad.getSimpleName() + "." + campo.getName())
						.isNotNull().extracting(Column::updatable).isEqualTo(false);
			}
			assertThat(Arrays.stream(entidad.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
					.map(Method::getName)).noneMatch(n -> n.startsWith("set"));
		}
		for (String tabla : new String[] { "curriculo_version", "area_curricular", "competencia", "escala_logro" }) {
			assertThat(permisos()).doesNotContainPattern("GRANT [^;]*ON cuentasclaras\\." + tabla + "\\b");
		}
	}

	@Test
	void lasAreasDeUnaAsignacionSonDeSoloInsercion() throws IOException {
		assertThat(permisos()).contains("GRANT INSERT ON cuentasclaras.asignacion_area TO 'cc_negocio';")
				.doesNotContainPattern("GRANT [^;]*(UPDATE|DELETE)[^;]*ON cuentasclaras\\.asignacion_area");
		for (String campo : new String[] { "asignacionId", "areaId" }) {
			assertThat(columna(AsignacionArea.class, campo).updatable()).as(campo).isFalse();
		}
		AsignacionArea area = AsignacionArea.de(1L, 2L);
		assertThatThrownBy(area::impedirCambio).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(area::impedirBorrado).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void laConfiguracionYLosPeriodosCambianSoloLasColumnasDeSuGrant() throws IOException {
		assertThat(ColumnasActualizables.de(ConfiguracionEvaluacion.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "configuracion_evaluacion"))
				.doesNotContain("anio_escolar_id", "colegio_id", "creado_por");
		assertThat(ColumnasActualizables.de(PeriodoEvaluacion.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "periodo_evaluacion"))
				.doesNotContain("numero", "anio_escolar_id", "colegio_id", "creado_por");
		assertThat(permisos()).doesNotContainPattern("GRANT [^;]*DELETE[^;]*ON cuentasclaras\\.(configuracion|periodo)_evaluacion");
	}

	@Test
	void unPeriodoCerradoNoSeReabreYEnAbiertoElFinSoloSeExtiende() {
		PeriodoEvaluacion p = PeriodoEvaluacion.programado(1L, 1, "I bimestre", LocalDate.of(2027, 3, 1),
				LocalDate.of(2027, 5, 7), "director");
		assertThatThrownBy(() -> p.cerrar("director", AHORA)).isInstanceOf(ReglaNegocioException.class);
		p.abrir("sistema.academico", AHORA);
		assertThatThrownBy(() -> p.moverFechas(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 5, 6), "director"))
				.isInstanceOf(ReglaNegocioException.class);
		p.moverFechas(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 5, 14), "director");
		assertThatThrownBy(() -> p.cerrar("sistema.academico", AHORA)).isInstanceOf(IllegalStateException.class);
		p.cerrar("director", AHORA);
		assertThat(p.getEstado()).isEqualTo(EstadoPeriodo.CERRADO);
		assertThatThrownBy(() -> p.abrir("director", AHORA)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> p.moverFechas(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 5, 21), "director"))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(columna(PeriodoEvaluacion.class, "numero").updatable()).isFalse();
		assertThat(Arrays.stream(PeriodoEvaluacion.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
				.map(Method::getName)).noneMatch(n -> n.startsWith("set"));
	}

	private static Column columna(Class<?> entidad, String campo) {
		try {
			return entidad.getDeclaredField(campo).getAnnotation(Column.class);
		}
		catch (NoSuchFieldException e) {
			throw new AssertionError(e);
		}
	}
}
