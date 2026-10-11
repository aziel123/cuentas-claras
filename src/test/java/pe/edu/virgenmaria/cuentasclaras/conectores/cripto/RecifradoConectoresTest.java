package pe.edu.virgenmaria.cuentasclaras.conectores.cripto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.Aad;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.SecretoIlegibleException;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 8, tanda 2 (decisión 118): la rotación de la clave maestra ({@code recifrar-conectores}). Cada secreto de la
 * versión anterior se descifra con la clave anterior y se vuelve a cifrar con la nueva, en una sola transacción; si uno no
 * se descifra, no cambia ninguno. Sobre H2 (en MySQL lo hace cc_migrador y trg_conector_colegio_estado solo le deja
 * cambiar el secreto y su versión hacia arriba).
 */
class RecifradoConectoresTest {

	private final String url = "jdbc:h2:mem:recifrado-" + UUID.randomUUID()
			+ ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

	private final byte[] claveA = CifradoAesGcm.leerClave(SecretosConectoresTest.CLAVE_A);

	private final byte[] claveB = CifradoAesGcm.leerClave(SecretosConectoresTest.CLAVE_B);

	/** Una sola conexión, abierta durante toda la prueba: H2 compila los CHECK con la sesión que migró. */
	private Connection conexion;

	@BeforeEach
	void migrar() throws Exception {
		conexion = DriverManager.getConnection(url, "sa", "");
		org.flywaydb.core.Flyway.configure()
				.dataSource(new org.springframework.jdbc.datasource.SingleConnectionDataSource(conexion, true))
				.locations("classpath:db/migration").load().migrate();
	}

	@org.junit.jupiter.api.AfterEach
	void cerrar() throws Exception {
		conexion.close();
	}

	private long insertar(Connection c, String clavePublica, byte[] cifrado, int version) throws Exception {
		try (PreparedStatement p = c.prepareStatement("INSERT INTO conector_colegio (colegio_id, tipo, proveedor, modo, "
				+ "configuracion, secreto_cifrado, secreto_pista, clave_version, clave_publica, estado, propuesto_por, "
				+ "creado_en, creado_por, actualizado_en) VALUES (1, 'OSE', 'NUBEFACT', 'REAL', '{}', ?, 'XYZ9', ?, ?, "
				+ "'PROPUESTO', 'administracion', NOW(), 'administracion', NOW())", new String[] { "id" })) {
			p.setBytes(1, cifrado);
			p.setInt(2, version);
			p.setString(3, clavePublica);
			p.executeUpdate();
			try (ResultSet r = p.getGeneratedKeys()) {
				r.next();
				return r.getLong(1);
			}
		}
	}

	private byte[] cifradoDe(Connection c, long id) throws Exception {
		try (ResultSet r = c.createStatement().executeQuery("SELECT secreto_cifrado FROM conector_colegio WHERE id = " + id)) {
			r.next();
			return r.getBytes(1);
		}
	}

	private int versionDe(Connection c, long id) throws Exception {
		try (ResultSet r = c.createStatement().executeQuery("SELECT clave_version FROM conector_colegio WHERE id = " + id)) {
			r.next();
			return r.getInt(1);
		}
	}

	@Test
	void vuelveACifrarConLaClaveNuevaYLaAnteriorYaNoSirve() throws Exception {
		String clave1 = UUID.randomUUID().toString();
		String clave2 = UUID.randomUUID().toString();
		{
			Connection c = conexion;
			long uno = insertar(c, clave1, CifradoAesGcm.cifrar(claveA, "token-uno-0123456789".getBytes(StandardCharsets.UTF_8),
					new Aad(1, "OSE", clave1)), 1);
			long dos = insertar(c, clave2, CifradoAesGcm.cifrar(claveA, "token-dos-0123456789".getBytes(StandardCharsets.UTF_8),
					new Aad(1, "OSE", clave2)), 1);

			RecifradoConectores.Resultado resultado = RecifradoConectores.recifrar(c, claveA, claveB, 2,
					LocalDateTime.of(2026, 10, 11, 10, 0));

			assertThat(resultado.recifrados()).isEqualTo(2);
			assertThat(versionDe(c, uno)).isEqualTo(2);
			assertThat(new String(CifradoAesGcm.descifrar(claveB, cifradoDe(c, uno), new Aad(1, "OSE", clave1)),
					StandardCharsets.UTF_8)).isEqualTo("token-uno-0123456789");
			assertThat(new String(CifradoAesGcm.descifrar(claveB, cifradoDe(c, dos), new Aad(1, "OSE", clave2)),
					StandardCharsets.UTF_8)).isEqualTo("token-dos-0123456789");
			assertThatThrownBy(() -> CifradoAesGcm.descifrar(claveA, cifradoDe(c, uno), new Aad(1, "OSE", clave1)))
					.isInstanceOf(SecretoIlegibleException.class);
			// Repetirla no hace nada (ya no quedan secretos de la versión 1).
			assertThat(RecifradoConectores.recifrar(c, claveA, claveB, 2, LocalDateTime.of(2026, 10, 11, 10, 5))
					.recifrados()).isZero();
		}
	}

	@Test
	void siUnSecretoNoSeDescifraNoCambiaNinguno() throws Exception {
		String clave1 = UUID.randomUUID().toString();
		String clave2 = UUID.randomUUID().toString();
		{
			Connection c = conexion;
			long bueno = insertar(c, clave1, CifradoAesGcm.cifrar(claveA, "token-bueno-012345".getBytes(StandardCharsets.UTF_8),
					new Aad(1, "OSE", clave1)), 1);
			// Uno copiado de otro conector (su AAD no es el de esta fila): no se descifra.
			insertar(c, clave2, cifradoDe(c, bueno), 1);
			byte[] antes = cifradoDe(c, bueno);

			assertThatThrownBy(() -> RecifradoConectores.recifrar(c, claveA, claveB, 2, LocalDateTime.of(2026, 10, 11, 10, 0)))
					.isInstanceOf(SecretoIlegibleException.class);

			assertThat(versionDe(c, bueno)).isEqualTo(1);
			assertThat(cifradoDe(c, bueno)).isEqualTo(antes);
		}
	}

	@Test
	void sinLaConfiguracionCompletaNoHaceNada() {
		assertThat(RecifradoConectores.ejecutarDesdeEntorno(Map.of("DB_URL", url))).isEqualTo(2);
		assertThat(RecifradoConectores.ejecutarDesdeEntorno(Map.of("DB_URL", url, "DB_MIGRADOR_USUARIO", "sa",
				"DB_MIGRADOR_CLAVE", "x", "CC_CLAVE_CONECTORES", SecretosConectoresTest.CLAVE_B,
				"CC_CLAVE_CONECTORES_VERSION", "1", "CC_CLAVE_CONECTORES_ANTERIOR", SecretosConectoresTest.CLAVE_A)))
				.isEqualTo(2);
	}
}
