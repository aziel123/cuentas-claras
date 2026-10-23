package pe.edu.virgenmaria.cuentasclaras.academico.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 9, tanda 2 (sección 6 «V33»): la asistencia y el consentimiento son de solo inserción (ninguna columna propia se
 * actualiza, la entidad rechaza un UPDATE o un borrado y {@code cc_app} no tiene UPDATE ni DELETE: 1142). La
 * configuración académica y la marca de agua actualizan EXACTAMENTE las columnas de su GRANT.
 */
class InmutabilidadAsistenciaTest {

	private static String permisos() throws IOException {
		return Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));
	}

	@Test
	void laAsistenciaYElConsentimientoSonDeSoloInsercion() throws IOException {
		Set<String> soloBase = new TreeSet<>(Set.of("actualizado_en", "version"));
		assertThat(ColumnasActualizables.de(RegistroAsistencia.class)).isEqualTo(soloBase);
		assertThat(ColumnasActualizables.de(MarcaAsistencia.class)).isEqualTo(soloBase);
		assertThat(ColumnasActualizables.de(ConsentimientoAviso.class)).isEqualTo(soloBase);
		String grants = permisos();
		for (String tabla : new String[] { "registro_asistencia", "marca_asistencia", "consentimiento_aviso" }) {
			assertThat(grants).contains("GRANT INSERT ON cuentasclaras." + tabla + " TO 'cc_negocio';")
					.doesNotContainPattern("GRANT [^;]*(UPDATE|DELETE)[^;]*ON cuentasclaras\\." + tabla + "\\b");
		}
	}

	@Test
	void laConfiguracionYLaMarcaDeAguaActualizanSoloSuGrant() throws IOException {
		assertThat(ColumnasActualizables.de(ConfiguracionAcademica.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "configuracion_academica"))
				.doesNotContain("colegio_id", "creado_por", "creado_en");
		assertThat(ColumnasActualizables.de(AvisoAsistenciaCorte.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "aviso_asistencia_corte"));
		// La cobertura no se alarga: hasta queda fuera del UPDATE de la asignación (1143).
		assertThat(ColumnasActualizables.concedidas(permisos(), "asignacion_docente")).doesNotContain("hasta");
		assertThat(ColumnasActualizables.de(AsignacionDocente.class)).doesNotContain("hasta");
	}

	@Test
	void sinSettersYSinFuenteMarcador() {
		for (Class<?> c : new Class<?>[] { RegistroAsistencia.class, MarcaAsistencia.class, ConsentimientoAviso.class,
				ConfiguracionAcademica.class, AvisoAsistenciaCorte.class }) {
			assertThat(Arrays.stream(c.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
					.map(Method::getName)).as(c.getSimpleName()).noneMatch(n -> n.startsWith("set"));
		}
		RegistroAsistencia registro = RegistroAsistencia.delDia(1L, 2L, LocalDate.of(2026, 10, 6), "ana", 3L, 1);
		assertThat(registro.getFuente()).isEqualTo(FuenteAsistencia.DOCENTE);
		assertThatThrownBy(registro::impedirCambio).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(registro::impedirBorrado).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unaFaltaJustificadaNoNaceEnLaHojaNiEnUnaCorreccionDelDia() {
		assertThatThrownBy(() -> MarcaAsistencia.inicial(RegistroAsistencia.delDia(1L, 2L, LocalDate.of(2026, 10, 6),
				"ana", 3L, 1), 4L, 5L, EstadoAsistencia.FALTA_JUSTIFICADA, null, 6L))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void elConsentimientoDeFichaExigeSuCodigo() {
		assertThatThrownBy(() -> ConsentimientoAviso.desdeFicha(1L, 2L, Finalidad.ASISTENCIA, Canal.WHATSAPP, "ab", "v1",
				3L)).isInstanceOf(ReglaNegocioException.class);
		assertThat(ConsentimientoAviso.desdeFicha(1L, 2L, Finalidad.ASISTENCIA, Canal.WHATSAPP, " FM-1 ", "v1", 3L)
				.getEvidencia()).isEqualTo("FM-1");
		assertThatThrownBy(() -> ConsentimientoAviso.enPortal(1L, 2L, Finalidad.ASISTENCIA, Canal.CORREO, true, true, "v1",
				3L)).isInstanceOf(ReglaNegocioException.class);
	}
}
