package pe.edu.virgenmaria.cuentasclaras.conciliacion;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.caja.model.VerificacionBancaria;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CuentaBancaria;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ExtractoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.LiquidacionLinea;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.LiquidacionPasarela;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 4, tanda 3: lo que la aplicación puede cambiar del extracto y la conciliación es exactamente lo que permite el
 * GRANT por columna de {@code 02-permisos-tablas.sql}. Los movimientos del banco y las liquidaciones son de solo
 * inserción; nada se borra; el saldo final, los montos y la cuenta de un extracto no se tocan.
 */
class InmutabilidadConciliacionTest {

	@Test
	void grantDeCuentaExtractoYPartidaCoincideConColumnasActualizables() throws IOException {
		String script = Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));

		assertThat(ColumnasActualizables.de(CuentaBancaria.class))
				.isEqualTo(ColumnasActualizables.concedidas(script, "cuenta_bancaria"))
				.containsExactlyInAnyOrder("activa", "actualizado_en", "version");
		assertThat(ColumnasActualizables.de(ExtractoBancario.class))
				.isEqualTo(ColumnasActualizables.concedidas(script, "extracto_bancario"))
				.doesNotContain("saldo_inicial", "saldo_final", "total_abonos", "total_cargos", "movimientos", "cuenta_id",
						"anterior_id", "secuencia", "archivo_id", "archivo_sha256", "desde", "hasta", "creado_por");
		assertThat(ColumnasActualizables.de(PartidaConciliacion.class))
				.isEqualTo(ColumnasActualizables.concedidas(script, "partida_conciliacion"))
				.containsExactlyInAnyOrder("estado", "movimiento_vigente", "objeto_vigente", "resuelto_por", "resuelto_en",
						"actualizado_en", "version");
		for (String tabla : List.of("movimiento_bancario", "liquidacion_pasarela", "liquidacion_linea")) {
			assertThat(script).as(tabla).containsPattern("GRANT INSERT ON cuentasclaras\\." + tabla + " ")
					.doesNotContainPattern("(?i)GRANT[^;]*UPDATE[^;]*ON cuentasclaras\\." + tabla + "\\b");
		}
		assertThat(script).doesNotContainPattern("(?i)GRANT[^;]*DELETE[^;]*ON cuentasclaras\\.(cuenta_bancaria|"
				+ "extracto_bancario|movimiento_bancario|partida_conciliacion|liquidacion_pasarela|liquidacion_linea)\\b");
		// La verificación bancaria sigue siendo de solo inserción (la automática nace con su partida).
		assertThat(script).doesNotContainPattern("(?i)GRANT[^;]*UPDATE[^;]*ON cuentasclaras\\.verificacion_bancaria\\b");
		assertThat(VerificacionBancaria.class.isAnnotationPresent(Immutable.class)).isTrue();
	}

	@Test
	void entidadesSinSettersYSinBorrado() {
		for (Class<?> entidad : List.of(CuentaBancaria.class, ExtractoBancario.class, MovimientoBancario.class,
				PartidaConciliacion.class, LiquidacionPasarela.class, LiquidacionLinea.class)) {
			assertThat(Arrays.stream(entidad.getDeclaredMethods())
					.filter(m -> Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("set"))
					.map(Method::getName)).as(entidad.getSimpleName()).isEmpty();
			assertThat(Arrays.stream(entidad.getDeclaredMethods())
					.filter(m -> m.isAnnotationPresent(jakarta.persistence.PreRemove.class))).as(entidad.getSimpleName())
					.hasSize(1);
		}
		for (Class<?> soloInsercion : List.of(MovimientoBancario.class, LiquidacionPasarela.class,
				LiquidacionLinea.class)) {
			assertThat(soloInsercion.isAnnotationPresent(Immutable.class)).as(soloInsercion.getSimpleName()).isTrue();
		}
	}
}
