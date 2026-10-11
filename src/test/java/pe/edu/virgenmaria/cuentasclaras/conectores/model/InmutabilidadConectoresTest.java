package pe.edu.virgenmaria.cuentasclaras.conectores.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 8, tanda 2 (sección 3.6, E14): un conector solo cambia su estado, su solicitud, su resolución y su prueba de
 * conexión. Su tipo, proveedor, modo, configuración, secreto cifrado, pista, versión de la clave, clave pública y quién lo
 * propuso no cambian ni por la entidad ({@code updatable = false}, sin setters) ni por SQL (GRANT por columna: 1143). Nunca
 * se borra.
 */
class InmutabilidadConectoresTest {

	private static String permisos() throws IOException {
		return Files.readString(Path.of("scripts/mysql/02-permisos-tablas.sql"));
	}

	@Test
	void lasColumnasActualizablesSonExactamenteLasDelGrant() throws IOException {
		assertThat(ColumnasActualizables.de(ConectorColegio.class))
				.isEqualTo(ColumnasActualizables.concedidas(permisos(), "conector_colegio"))
				.doesNotContain("tipo", "proveedor", "modo", "configuracion", "secreto_cifrado", "secreto_pista",
						"clave_version", "clave_publica", "propuesto_por", "colegio_id", "creado_por", "creado_en");
		assertThat(permisos()).doesNotContainPattern("GRANT [^;]*DELETE[^;]*ON cuentasclaras\\.conector_colegio");
	}

	@Test
	void noTieneSettersYSoloCambiaPorSusTransiciones() {
		assertThat(Arrays.stream(ConectorColegio.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
				.map(Method::getName)).noneMatch(n -> n.startsWith("set"));
		ConectorColegio c = ConectorColegio.propuesto(TipoConector.CUENTA_BANCARIA, ProveedorConector.BCP, ModoConector.REAL,
				"{}", "0f0e0d0c-0b0a-4908-8706-050403020100", null, null, null, "administracion");
		LocalDateTime ahora = LocalDateTime.of(2026, 10, 11, 9, 0);

		assertThatThrownBy(() -> c.entrarEnVigencia("promotor", ahora)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("probar la conexión");
		c.registrarPrueba(true, null, ahora);
		assertThatThrownBy(() -> c.entrarEnVigencia("administracion", ahora)).isInstanceOf(IllegalStateException.class);
		c.entrarEnVigencia("promotor", ahora);
		assertThat(c.getEstado()).isEqualTo(EstadoConector.VIGENTE);
		assertThatThrownBy(() -> c.rechazar("otro", ahora)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> c.registrarPrueba(false, "x", ahora)).isInstanceOf(ReglaNegocioException.class);
		c.reemplazar();
		assertThat(c.getEstado()).isEqualTo(EstadoConector.REEMPLAZADO);
		assertThat(c.isVigente()).isFalse();
		assertThatThrownBy(c::impedirBorrado).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void elProveedorYElModoDebenSerDelTipo() {
		assertThatThrownBy(() -> ConectorColegio.propuesto(TipoConector.OSE, ProveedorConector.BCP, ModoConector.REAL, "{}",
				"0f0e0d0c-0b0a-4908-8706-050403020100", null, null, null, "a")).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> ConectorColegio.propuesto(TipoConector.OSE, ProveedorConector.SIMULADO, ModoConector.REAL,
				"{}", "0f0e0d0c-0b0a-4908-8706-050403020100", null, null, null, "a")).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> ConectorColegio.propuesto(TipoConector.CUENTA_BANCARIA, ProveedorConector.BCP,
				ModoConector.PRUEBAS, "{}", "0f0e0d0c-0b0a-4908-8706-050403020100", null, null, null, "a"))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(TipoConector.OSE.mueveDinero()).isFalse();
		assertThat(List.of(TipoConector.PASARELA, TipoConector.CUENTA_BANCARIA, TipoConector.RECAUDACION))
				.allMatch(TipoConector::mueveDinero);
	}

	@Test
	void ningunTextoDelConectorLlevaElSecreto() {
		ConectorVigente vigente = new ConectorVigente(1L, 0L, 3L, TipoConector.OSE, ProveedorConector.NUBEFACT,
				ModoConector.REAL, "0f0e0d0c-0b0a-4908-8706-050403020100",
				new ConfiguracionConector.Ose("https://api.nubefact.com/api/v1/x", "20600000013", "B002", "F002", "BC02",
						"FC02", "INAFECTO"), Map.of(ConectorVigente.TOKEN, "token-super-secreto-1234567890"));

		assertThat(vigente.toString()).doesNotContain("token-super-secreto");
		assertThat(vigente.configuracion().resumen()).doesNotContain("token");
		assertThat(new ConfiguracionConector.CuentaBancaria("1912345678901", "00219100123456789012", "PEN").resumen())
				.contains("8901").doesNotContain("1912345678901").doesNotContain("00219100123456789012");
	}
}
