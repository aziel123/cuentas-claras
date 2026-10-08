package pe.edu.virgenmaria.cuentasclaras.panel.model;

import jakarta.persistence.Column;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 6, tanda 2: la foto del resumen diario es de SOLO INSERCIÓN: ninguna columna propia actualizable (un save de
 * una entidad cargada intentaría un UPDATE que MySQL rechaza con 1142), {@code @Immutable}, {@code @PreUpdate} y
 * {@code @PreRemove} que fallan, y en 02-permisos-tablas.sql solo un GRANT INSERT.
 */
class InmutabilidadPanelTest {

	@Test
	void ningunaColumnaDeLaFotoEsActualizable() {
		assertThat(ColumnasActualizables.de(ResumenDiario.class)).as("solo las de BaseEntity")
				.containsExactlyInAnyOrder("actualizado_en", "version");
		assertThat(Arrays.stream(ResumenDiario.class.getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(Column.class)).filter(f -> f.getAnnotation(Column.class).updatable()))
				.isEmpty();
		assertThat(ResumenDiario.class.isAnnotationPresent(Immutable.class)).isTrue();
		assertThat(BaseEntity.class.isAssignableFrom(ResumenDiario.class)).isTrue();
	}

	@Test
	void editarOBorrarLaFotoFalla() {
		ResumenDiario foto = ResumenDiario.de(LocalDate.of(2027, 4, 15), LocalDateTime.of(2027, 4, 15, 19, 30),
				new ResumenDiario.Cifras(new BigDecimal("800.00"), 2, new BigDecimal("350.00"), 1, new BigDecimal("800.00"),
						new BigDecimal("1600.00"), 2),
				new ResumenDiario.Conteos(1, 0, 0, 0, 0, 0), null, null);
		int anotados = 0;
		for (Method m : ResumenDiario.class.getDeclaredMethods()) {
			if (m.isAnnotationPresent(PreUpdate.class) || m.isAnnotationPresent(PreRemove.class)) {
				anotados++;
				m.setAccessible(true);
				assertThatThrownBy(() -> m.invoke(foto)).hasCauseInstanceOf(IllegalStateException.class);
			}
		}
		assertThat(anotados).isEqualTo(2);
		assertThat(Arrays.stream(ResumenDiario.class.getDeclaredMethods()).map(Method::getName))
				.noneMatch(n -> n.startsWith("set"));
	}

	@Test
	void laHuellaVaConSuSecuenciaYSuCodigo() {
		assertThatThrownBy(() -> ResumenDiario.de(LocalDate.of(2027, 4, 15), LocalDateTime.of(2027, 4, 15, 19, 30),
				new ResumenDiario.Cifras(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0),
				new ResumenDiario.Conteos(0, 0, 0, 0, 0, 0), 5L, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void enMySqlCcAppSoloInsertaFotos() throws IOException {
		String script = Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));
		assertThat(script).contains("GRANT INSERT ON cuentasclaras.resumen_diario TO 'cc_app'@'%';")
				.doesNotContainPattern("(?m)^GRANT[^;]*(UPDATE|DELETE)[^;]*ON cuentasclaras\\.resumen_diario ");
	}
}
