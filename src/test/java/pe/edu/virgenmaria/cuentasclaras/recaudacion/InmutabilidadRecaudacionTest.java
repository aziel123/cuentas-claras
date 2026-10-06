package pe.edu.virgenmaria.cuentasclaras.recaudacion;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.ArchivoCargado;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LineaRecaudacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.LoteRecaudacion;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 4, tanda 2: lo que una persona o la aplicación pueden cambiar de la recaudación es exactamente lo que permite el
 * GRANT por columna de {@code 02-permisos-tablas.sql} (si no, MySQL respondería 1143 o dejaría cambiar de más). El
 * archivo original del banco es de solo inserción y nada se borra.
 */
class InmutabilidadRecaudacionTest {

	@Test
	void grantDeLoteYLineaCoincideConColumnasActualizables() throws IOException {
		String script = Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));

		assertThat(ColumnasActualizables.de(LoteRecaudacion.class))
				.isEqualTo(ColumnasActualizables.concedidas(script, "lote_recaudacion"))
				.doesNotContain("total", "total_banco", "archivo_id", "archivo_sha256", "lineas", "desde", "hasta",
						"banco");
		assertThat(ColumnasActualizables.de(LineaRecaudacion.class))
				.isEqualTo(ColumnasActualizables.concedidas(script, "linea_recaudacion"))
				.containsExactlyInAnyOrder("estado", "motivo_excepcion", "detalle", "devolucion_operacion", "devuelto_por",
						"devuelto_en", "actualizado_en", "version");
		// El archivo original: solo inserción y @Immutable.
		assertThat(script).containsPattern("GRANT INSERT ON cuentasclaras\\.archivo_cargado ")
				.doesNotContainPattern("(?i)GRANT[^;]*UPDATE[^;]*ON cuentasclaras\\.archivo_cargado\\b");
		assertThat(ArchivoCargado.class.isAnnotationPresent(Immutable.class)).isTrue();
		assertThat(script).doesNotContainPattern("(?i)GRANT[^;]*DELETE[^;]*ON cuentasclaras\\.(archivo_cargado|"
				+ "lote_recaudacion|linea_recaudacion)\\b");
		// La línea de un pago no cambia: pago no gana columnas en su GRANT.
		assertThat(ColumnasActualizables.concedidas(script, "pago")).doesNotContain("linea_recaudacion_id");
	}

	@Test
	void entidadesSinSettersYSinBorrado() {
		for (Class<?> entidad : List.of(LoteRecaudacion.class, LineaRecaudacion.class, ArchivoCargado.class)) {
			assertThat(Arrays.stream(entidad.getDeclaredMethods())
					.filter(m -> Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("set"))
					.map(Method::getName)).as(entidad.getSimpleName()).isEmpty();
			assertThat(Arrays.stream(entidad.getDeclaredMethods())
					.filter(m -> m.isAnnotationPresent(jakarta.persistence.PreRemove.class))).as(entidad.getSimpleName())
					.hasSize(1);
		}
	}

	/** El modelo no deja confirmar a quien subió el archivo ni con otro total, ni volver atrás. */
	@Test
	void elLoteNoSeConfirmaPorQuienLoSubioNiConOtroTotal() throws Exception {
		LoteRecaudacion lote = LoteRecaudacion.registrar(1L, "a".repeat(64),
				pe.edu.virgenmaria.cuentasclaras.recaudacion.model.BancoRecaudacion.GENERICO, "GENERICO_CSV",
				java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 1),
				java.time.LocalDate.of(2026, 10, 1), 1, new java.math.BigDecimal("450.00"), null);
		var creadoPor = pe.edu.virgenmaria.cuentasclaras.comun.model.BaseEntity.class.getDeclaredField("creadoPor");
		creadoPor.setAccessible(true);
		creadoPor.set(lote, "administracion");
		java.time.LocalDateTime ahora = java.time.LocalDateTime.of(2026, 10, 2, 9, 0);

		assertThatThrownBy(() -> lote.confirmar("administracion", new java.math.BigDecimal("450.00"), ahora))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> lote.confirmar("promotor", new java.math.BigDecimal("450.01"), ahora))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(lote.intentoFallido(2, "promotor", ahora)).isFalse();
		assertThat(lote.intentoFallido(2, "director", ahora)).isTrue();
		assertThat(lote.getShaVigente()).isNull();
		assertThatThrownBy(() -> lote.confirmar("promotor", new java.math.BigDecimal("450.00"), ahora))
				.isInstanceOf(pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException.class);
	}
}
