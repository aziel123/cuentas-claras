package pe.edu.virgenmaria.cuentasclaras.conectores.cripto;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.Aad;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.CifradoAesGcm.SecretoIlegibleException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Modo {@code java -jar cuentas-claras.jar recifrar-conectores} (sprint 8, tanda 2; decisión 118): la rotación de la
 * clave maestra de los secretos de los conectores. Sin Spring ni servidor web (como {@code migrar}), con el usuario
 * {@code cc_migrador}: los secretos no cambian para cc_app ni cc_sistema (GRANT por columna, 1143) y
 * trg_conector_colegio_estado solo admite, a cc_migrador, cambiar el secreto cifrado y su versión hacia una versión mayor.
 * <ol>
 *   <li>Lee cada conector con secreto cifrado con la versión ANTERIOR ({@code CC_CLAVE_CONECTORES_VERSION - 1}).</li>
 *   <li>Lo descifra con {@code CC_CLAVE_CONECTORES_ANTERIOR} (el AAD de su colegio, tipo y clave pública lo autentica) y
 *       lo vuelve a cifrar con {@code CC_CLAVE_CONECTORES} (nonce nuevo), en la misma fila y con su {@code version}
 *       (bloqueo optimista).</li>
 *   <li>Todo en una transacción: si un secreto no se descifra, no se cambia ninguno.</li>
 * </ol>
 * Variables: {@code DB_URL}, {@code DB_MIGRADOR_USUARIO}, {@code DB_MIGRADOR_CLAVE}, {@code CC_CLAVE_CONECTORES},
 * {@code CC_CLAVE_CONECTORES_VERSION} (2 o más) y {@code CC_CLAVE_CONECTORES_ANTERIOR}. El log nunca lleva un secreto ni
 * una clave: solo ids, colegios y conteos. Después se reinicia la aplicación con la clave nueva (sin la anterior).
 */
public final class RecifradoConectores {

	public static final String ARGUMENTO = "recifrar-conectores";

	private static final Logger LOG = LoggerFactory.getLogger(RecifradoConectores.class);

	static final String SQL_PENDIENTES = "SELECT id, colegio_id, tipo, clave_publica, secreto_cifrado, version "
			+ "FROM conector_colegio WHERE secreto_cifrado IS NOT NULL AND clave_version = ? ORDER BY id";

	static final String SQL_RECIFRAR = "UPDATE conector_colegio SET secreto_cifrado = ?, clave_version = ?, "
			+ "actualizado_en = ?, version = version + 1 WHERE id = ? AND version = ? AND clave_version = ?";

	private RecifradoConectores() {
	}

	/** El resultado: cuántos secretos se volvieron a cifrar. */
	public record Resultado(int recifrados, int versionAnterior, int versionNueva) {
	}

	/** @return el código de salida: 0 si todo salió bien, 1 si falló, 2 si falta configuración */
	public static int ejecutarDesdeEntorno(Map<String, String> entorno) {
		if (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) instanceof ch.qos.logback.classic.Logger raiz) {
			raiz.setLevel(Level.INFO);
		}
		List<String> faltantes = new ArrayList<>();
		for (String variable : List.of("DB_URL", "DB_MIGRADOR_USUARIO", "DB_MIGRADOR_CLAVE", "CC_CLAVE_CONECTORES",
				"CC_CLAVE_CONECTORES_VERSION", "CC_CLAVE_CONECTORES_ANTERIOR")) {
			if (entorno.get(variable) == null || entorno.get(variable).isBlank()) {
				faltantes.add(variable);
			}
		}
		if (!faltantes.isEmpty()) {
			LOG.error("No se pueden volver a cifrar los secretos de los conectores: faltan las variables {}",
					String.join(", ", faltantes));
			return 2;
		}
		int version;
		byte[] nueva;
		byte[] anterior;
		try {
			version = Integer.parseInt(entorno.get("CC_CLAVE_CONECTORES_VERSION").strip());
			nueva = CifradoAesGcm.leerClave(entorno.get("CC_CLAVE_CONECTORES"));
			anterior = CifradoAesGcm.leerClave(entorno.get("CC_CLAVE_CONECTORES_ANTERIOR"));
		}
		catch (IllegalArgumentException e) {
			LOG.error("Configuración de la rotación no válida: {}", e.getMessage());
			return 2;
		}
		if (version < 2) {
			LOG.error("CC_CLAVE_CONECTORES_VERSION debe ser 2 o más (la anterior es la versión de antes).");
			return 2;
		}
		try (Connection conexion = DriverManager.getConnection(entorno.get("DB_URL"), entorno.get("DB_MIGRADOR_USUARIO"),
				entorno.get("DB_MIGRADOR_CLAVE"))) {
			Resultado resultado = recifrar(conexion, anterior, nueva, version, LocalDateTime.now(ZoneId.of("America/Lima")));
			LOG.info("Secretos de los conectores cifrados de nuevo: {} de la versión {} a la {}.", resultado.recifrados(),
					resultado.versionAnterior(), resultado.versionNueva());
			return 0;
		}
		catch (SQLException | RuntimeException e) {
			LOG.error("No se volvieron a cifrar los secretos de los conectores (no se cambió ninguno): {}",
					e instanceof SecretoIlegibleException ? e.getMessage() : e.getClass().getSimpleName() + ": "
							+ e.getMessage());
			return 1;
		}
		finally {
			Arrays.fill(nueva, (byte) 0);
			Arrays.fill(anterior, (byte) 0);
		}
	}

	/**
	 * Vuelve a cifrar, en UNA transacción, los secretos de la versión {@code versionNueva - 1}. Si alguno no se descifra con
	 * la clave anterior, deshace todo y lanza {@link SecretoIlegibleException}.
	 */
	public static Resultado recifrar(Connection conexion, byte[] claveAnterior, byte[] claveNueva, int versionNueva,
			LocalDateTime ahora) throws SQLException {
		int versionAnterior = versionNueva - 1;
		boolean autocommit = conexion.getAutoCommit();
		conexion.setAutoCommit(false);
		int recifrados = 0;
		try {
			record Fila(long id, long colegio, String tipo, String clavePublica, byte[] cifrado, long version) {
			}
			List<Fila> filas = new ArrayList<>();
			try (PreparedStatement consulta = conexion.prepareStatement(SQL_PENDIENTES)) {
				consulta.setInt(1, versionAnterior);
				try (ResultSet r = consulta.executeQuery()) {
					while (r.next()) {
						filas.add(new Fila(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getBytes(5),
								r.getLong(6)));
					}
				}
			}
			try (PreparedStatement cambio = conexion.prepareStatement(SQL_RECIFRAR)) {
				for (Fila f : filas) {
					Aad aad = new Aad(f.colegio(), f.tipo(), f.clavePublica());
					byte[] claro = CifradoAesGcm.descifrar(claveAnterior, f.cifrado(), aad);
					byte[] nuevo;
					try {
						nuevo = CifradoAesGcm.cifrar(claveNueva, claro, aad);
					}
					finally {
						Arrays.fill(claro, (byte) 0);
					}
					cambio.setBytes(1, nuevo);
					cambio.setInt(2, versionNueva);
					cambio.setObject(3, ahora);
					cambio.setLong(4, f.id());
					cambio.setLong(5, f.version());
					cambio.setInt(6, versionAnterior);
					if (cambio.executeUpdate() != 1) {
						throw new IllegalStateException("El conector " + f.id() + " cambió mientras se volvía a cifrar: "
								+ "repite la rotación.");
					}
					recifrados++;
					LOG.info("Conector {} (colegio {}, {}): secreto cifrado de nuevo.", f.id(), f.colegio(), f.tipo());
				}
			}
			conexion.commit();
			return new Resultado(recifrados, versionAnterior, versionNueva);
		}
		catch (SQLException | RuntimeException e) {
			conexion.rollback();
			throw e;
		}
		finally {
			conexion.setAutoCommit(autocommit);
		}
	}
}
