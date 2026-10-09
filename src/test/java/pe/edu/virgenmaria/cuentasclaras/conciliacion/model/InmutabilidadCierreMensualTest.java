package pe.edu.virgenmaria.cuentasclaras.conciliacion.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Feriado;
import pe.edu.virgenmaria.cuentasclaras.comun.muestreo.SemillaMuestreo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 5, tanda 3: el cierre mensual no cambia su cuenta, su mes ni sus totales calculados; el feriado no cambia su
 * fecha; la semilla del muestreo es de solo inserción. Ni por la entidad ({@code updatable = false}, sin setters) ni por
 * SQL (GRANT por columna: 1143; sin UPDATE: 1142). Las listas coinciden EXACTAMENTE con 02-permisos-tablas.sql.
 */
class InmutabilidadCierreMensualTest {

	private static String permisos() throws IOException {
		return Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));
	}

	@Test
	void lasColumnasActualizablesDelCierreSonExactamenteLasDelGrant() throws IOException {
		assertThat(ColumnasActualizables.de(CierreMensualBanco.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "cierre_mensual_banco"))
				.doesNotContain("cuenta_id", "anio", "mes", "total_abonos", "total_cargos", "saldo_final");
	}

	@Test
	void lasColumnasActualizablesDelFeriadoSonExactamenteLasDelGrant() throws IOException {
		assertThat(ColumnasActualizables.de(Feriado.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "feriado"))
				.doesNotContain("fecha", "descripcion");
	}

	@Test
	void laSemillaEsDeSoloInsercionYNadaSeBorra() throws IOException {
		assertThat(ColumnasActualizables.de(SemillaMuestreo.class)).containsOnly("actualizado_en", "version");
		assertThat(permisos()).contains("GRANT INSERT ON cuentasclaras.semilla_muestreo TO 'cc_sistema'@'%';")
				.doesNotContainPattern("GRANT [^;]*UPDATE[^;]*ON cuentasclaras\\.semilla_muestreo")
				.doesNotContainPattern("GRANT [^;]*DELETE[^;]*ON cuentasclaras\\.(feriado|semilla_muestreo|cierre_mensual_banco)");
	}

	@Test
	void sinSettersYUnCierreResueltoNoCambia() {
		for (Class<?> clase : new Class<?>[] { CierreMensualBanco.class, Feriado.class, SemillaMuestreo.class }) {
			assertThat(Arrays.stream(clase.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
					.map(Method::getName)).as(clase.getSimpleName()).noneMatch(n -> n.startsWith("set"));
		}
		CierreMensualBanco cierre = CierreMensualBanco.nuevo(cuenta(), YearMonth.of(2026, 9),
				new BigDecimal("1800.00"), new BigDecimal("300.35"), new BigDecimal("11499.65"));
		LocalDateTime ahora = LocalDateTime.of(2026, 10, 2, 9, 0);
		assertThat(cierre.intentar(new BigDecimal("1800"), new BigDecimal("300.35"), new BigDecimal("11499.65"),
				"director", ahora)).isEqualTo(EstadoCierreMensual.CUADRADO);
		assertThatThrownBy(() -> cierre.intentar(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "director", ahora))
				.isInstanceOf(IllegalStateException.class);
	}

	private static CuentaBancaria cuenta() {
		try {
			var constructor = CuentaBancaria.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
