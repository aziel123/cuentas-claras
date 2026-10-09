package pe.edu.virgenmaria.cuentasclaras.operacion.respaldo;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.CamposSellados;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.HuellasObjetosBd;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.SelladorAuditoria;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Modo {@code java -jar cuentas-claras.jar verificar-respaldo} (sprint 7, tanda 1): comprueba una COPIA RESTAURADA de un
 * respaldo, sin levantar Spring ni el servidor web (como {@code migrar}). Lo llama
 * {@code scripts/respaldo/restaurar-y-verificar.sh} después de cargar los datos y antes de arrancar la aplicación.
 * <ol>
 *   <li>La versión de Flyway de la copia es la del manifiesto.</li>
 *   <li>La cadena de la bitácora SIN la clave: secuencias seguidas desde 1, el eslabón en el último evento, el ancla
 *       «antes» del manifiesto presente con su hash, el último evento entre las anclas «antes» y «después», las anclas
 *       de los manifiestos anteriores y las huellas diaria y por hora con su código.</li>
 *   <li>Con {@code AUDITORIA_CLAVE_HMAC} (simulacro presencial y CI): el HMAC de cada evento, encadenado. Un evento
 *       cambiado en el volcado se detecta aquí: «la cadena no verifica en la secuencia N».</li>
 *   <li>Los conteos de las tablas de solo inserción: las filas con id hasta el máximo del manifiesto son al menos tantas
 *       como las del manifiesto (si faltan, se borraron del volcado).</li>
 *   <li>La consistencia de los libros ({@code db/respaldo/comprobaciones.sql}): al cargar, los triggers no corrieron.</li>
 * </ol>
 * Variables: {@code DB_URL}, {@code DB_USUARIO}, {@code DB_CLAVE} (basta con lectura: cc_app),
 * {@code RESPALDO_MANIFIESTO} (ruta del manifiesto), y opcionales {@code RESPALDO_MANIFIESTOS_ANTERIORES} (carpeta con
 * los manifiestos de respaldos anteriores), {@code AUDITORIA_CLAVE_HMAC} y {@code RESPALDO_INFORME} (ruta donde escribe
 * el resultado en JSON). Nunca registra datos personales: solo secuencias, tablas y conteos.
 */
public final class VerificadorRespaldo {

	public static final String ARGUMENTO = "verificar-respaldo";

	static final String COMPROBACIONES = "/db/respaldo/comprobaciones.sql";

	static final String HASH_INICIAL = "0".repeat(64);

	private static final Pattern TABLA = Pattern.compile("[a-z][a-z0-9_]{0,63}");

	private static final int LOTE = 1000;

	private static final Logger LOG = LoggerFactory.getLogger(VerificadorRespaldo.class);

	/** Resultado de una comprobación: nombre corto, si pasó y el detalle (sin datos personales). */
	public record Comprobacion(String nombre, boolean paso, String detalle) {
	}

	private final List<Comprobacion> resultados = new ArrayList<>();

	private VerificadorRespaldo() {
	}

	/** @return el código de salida: 0 si todo pasó, 1 si alguna comprobación falló, 2 si falta configuración */
	public static int ejecutarDesdeEntorno(Map<String, String> entorno) {
		silenciarDepuracion();
		List<String> faltantes = Stream.of("DB_URL", "DB_USUARIO", "DB_CLAVE", "RESPALDO_MANIFIESTO")
				.filter(v -> entorno.get(v) == null || (!v.equals("DB_CLAVE") && entorno.get(v).isBlank())).toList();

		if (!faltantes.isEmpty()) {
			LOG.error("No se puede verificar el respaldo: faltan las variables {}", String.join(", ", faltantes));
			return 2;
		}
		String clave = entorno.get("AUDITORIA_CLAVE_HMAC");
		if (clave != null && !clave.isBlank() && clave.length() < SelladorAuditoria.LONGITUD_MINIMA_CLAVE) {
			LOG.error("No se puede verificar el respaldo: la clave HMAC tiene menos de {} caracteres.",
					SelladorAuditoria.LONGITUD_MINIMA_CLAVE);
			return 2;
		}
		JsonNode manifiesto;
		List<JsonNode> anteriores = new ArrayList<>();
		try {
			manifiesto = leerManifiesto(Path.of(entorno.get("RESPALDO_MANIFIESTO")));
			String carpeta = entorno.get("RESPALDO_MANIFIESTOS_ANTERIORES");
			if (carpeta != null && !carpeta.isBlank()) {
				try (Stream<Path> archivos = Files.list(Path.of(carpeta))) {
					for (Path archivo : archivos.filter(p -> p.getFileName().toString().endsWith(".json")).sorted()
							.toList()) {
						anteriores.add(leerManifiesto(archivo));
					}
				}
			}
		}
		catch (IOException | RuntimeException e) {
			LOG.error("No se pudo leer el manifiesto: {}", e.getMessage());
			return 2;
		}
		VerificadorRespaldo verificador = new VerificadorRespaldo();
		String huellasAdmin = entorno.get("RESPALDO_HUELLAS_ADMIN");
		if (huellasAdmin != null && !huellasAdmin.isBlank()) {
			verificador.verificarObjetos(Path.of(huellasAdmin));
		}
		try (Connection conexion = DriverManager.getConnection(entorno.get("DB_URL"), entorno.get("DB_USUARIO"),
				entorno.get("DB_CLAVE"))) {
			conexion.setReadOnly(true);
			verificador.comprobar(conexion, manifiesto, anteriores,
					clave == null || clave.isBlank() ? null : new SelladorAuditoria(clave));
		}
		catch (SQLException | RuntimeException e) {
			verificador.registrar("conexion", false, "No se pudo completar la verificación: " + e.getMessage());
		}
		String informe = entorno.get("RESPALDO_INFORME");
		if (informe != null && !informe.isBlank()) {
			try {
				Files.writeString(Path.of(informe), verificador.informeJson(manifiesto), StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				LOG.error("No se pudo escribir el informe {}: {}", informe, e.getMessage());
				return 1;
			}
		}
		long fallas = verificador.resultados.stream().filter(c -> !c.paso()).count();
		if (fallas > 0) {
			LOG.error("El respaldo {} NO pasó la verificación: {} comprobaciones fallaron.", texto(manifiesto, "archivo"),
					fallas);
			return 1;
		}
		LOG.info("El respaldo {} pasó la verificación ({} comprobaciones).", texto(manifiesto, "archivo"),
				verificador.resultados.size());
		return 0;
	}

	/** Corre todas las comprobaciones sobre la copia (también desde las pruebas, con H2). */
	public static List<Comprobacion> verificar(Connection conexion, JsonNode manifiesto, List<JsonNode> anteriores,
			SelladorAuditoria sellador) throws SQLException {
		VerificadorRespaldo verificador = new VerificadorRespaldo();
		verificador.comprobar(conexion, manifiesto, anteriores, sellador);
		return List.copyOf(verificador.resultados);
	}

	private void comprobar(Connection conexion, JsonNode manifiesto, List<JsonNode> anteriores,
			SelladorAuditoria sellador) throws SQLException {
		verificarVersion(conexion, manifiesto);
		verificarCadenaSinClave(conexion, manifiesto, anteriores);
		if (sellador != null) {
			verificarHmac(conexion, sellador);
		}
		else {
			registrar("hmac", true, "Sin AUDITORIA_CLAVE_HMAC: no se recalculó el HMAC (lo hace el simulacro presencial).");
		}
		verificarConteos(conexion, manifiesto);
		verificarHuellas(conexion);
		verificarLibros(conexion);
	}

	private void verificarVersion(Connection conexion, JsonNode manifiesto) throws SQLException {
		String esperada = texto(manifiesto, "version_esquema");
		long maxima = -1;
		try (Statement sentencia = conexion.createStatement();
				ResultSet filas = sentencia.executeQuery(
						"SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL")) {
			while (filas.next()) {
				maxima = Math.max(maxima, Long.parseLong(filas.getString(1).split("\\.")[0]));
			}
		}
		registrar("version_esquema", String.valueOf(maxima).equals(esperada),
				"La copia está en la versión " + maxima + " y el manifiesto dice " + esperada + ".");
	}

	/** @return la última secuencia de la copia */
	private long verificarCadenaSinClave(Connection conexion, JsonNode manifiesto, List<JsonNode> anteriores)
			throws SQLException {
		long cantidad;
		long minima;
		long maxima;
		try (Statement sentencia = conexion.createStatement();
				ResultSet fila = sentencia.executeQuery(
						"SELECT COUNT(*), COALESCE(MIN(secuencia), 0), COALESCE(MAX(secuencia), 0) FROM evento_auditoria")) {
			fila.next();
			cantidad = fila.getLong(1);
			minima = fila.getLong(2);
			maxima = fila.getLong(3);
		}
		boolean seguidas = cantidad == maxima && (cantidad == 0 || minima == 1);
		registrar("secuencias_seguidas", seguidas, seguidas ? "Los " + cantidad + " eventos van de 1 a " + maxima + "."
				: "La bitácora tiene " + cantidad + " eventos entre " + minima + " y " + maxima
						+ ": faltan o sobran eventos (primer hueco en " + primerHueco(conexion) + ").");

		String hashUltimo = hashDe(conexion, maxima);
		long eslabonSecuencia = -1;
		String eslabonHash = null;
		try (Statement sentencia = conexion.createStatement();
				ResultSet fila = sentencia.executeQuery(
						"SELECT ultima_secuencia, ultimo_hash FROM auditoria_cadena WHERE id = 1")) {
			if (fila.next()) {
				eslabonSecuencia = fila.getLong(1);
				eslabonHash = fila.getString(2);
			}
		}
		boolean eslabon = eslabonSecuencia == maxima
				&& (maxima == 0 ? HASH_INICIAL.equals(eslabonHash) : hashUltimo != null && hashUltimo.equals(eslabonHash));
		registrar("eslabon_en_el_ultimo_evento", eslabon, eslabon ? "El eslabón apunta al evento " + maxima + "."
				: "El eslabón dice " + eslabonSecuencia + " y el último evento de la copia es " + maxima + ".");

		long antes = numero(manifiesto, "secuencia_antes");
		long despues = numero(manifiesto, "secuencia_despues");
		boolean anclaAntes = antes == 0 ? HASH_INICIAL.equals(texto(manifiesto, "hash_antes"))
				: texto(manifiesto, "hash_antes").equals(hashDe(conexion, antes));
		registrar("ancla_antes", anclaAntes, anclaAntes ? "El evento " + antes + " del ancla «antes» está con su hash."
				: "El evento " + antes + " del ancla «antes» no está en la copia o su hash cambió.");
		boolean entreAnclas = maxima >= antes && maxima <= despues
				&& (maxima != despues || texto(manifiesto, "hash_despues").equals(hashUltimo));
		registrar("ultimo_evento_entre_anclas", entreAnclas, "El último evento de la copia es " + maxima
				+ " y el volcado se tomó entre el " + antes + " y el " + despues + ".");

		for (JsonNode anterior : anteriores) {
			long secuencia = numero(anterior, "secuencia_despues");
			String archivo = texto(anterior, "archivo");
			boolean presente = secuencia == 0 || (secuencia <= maxima
					&& texto(anterior, "hash_despues").equals(hashDe(conexion, secuencia)));
			registrar("ancla_de_" + archivo, presente, presente
					? "El ancla del respaldo anterior " + archivo + " (evento " + secuencia + ") está con su hash."
					: "El ancla del respaldo anterior " + archivo + " (evento " + secuencia
							+ ") no está en la copia o su hash cambió: la bitácora fue recortada o alterada.");
		}
		return maxima;
	}

	private void verificarHmac(Connection conexion, SelladorAuditoria sellador) throws SQLException {
		String anterior = HASH_INICIAL;
		long cursor = 0;
		long revisados = 0;
		try (PreparedStatement consulta = conexion.prepareStatement("SELECT secuencia, colegio_id, ocurrido_en, "
				+ "usuario_id, nombre_usuario, roles, accion, entidad, entidad_id, valor_anterior, valor_nuevo, detalle, "
				+ "ip, hash FROM evento_auditoria WHERE secuencia > ? ORDER BY secuencia LIMIT " + LOTE)) {
			int leidos;
			do {
				consulta.setLong(1, cursor);
				leidos = 0;
				try (ResultSet filas = consulta.executeQuery()) {
					while (filas.next()) {
						leidos++;
						CamposSellados campos = new CamposSellados(filas.getLong("secuencia"),
								(Long) filas.getObject("colegio_id", Long.class),
								filas.getObject("ocurrido_en", LocalDateTime.class),
								(Long) filas.getObject("usuario_id", Long.class), filas.getString("nombre_usuario"),
								filas.getString("roles"), filas.getString("accion"), filas.getString("entidad"),
								filas.getString("entidad_id"), filas.getString("valor_anterior"),
								filas.getString("valor_nuevo"), filas.getString("detalle"), filas.getString("ip"));
						String guardado = filas.getString("hash");
						if (!sellador.sellar(anterior, campos).equals(guardado)) {
							registrar("hmac", false, "La cadena no verifica en la secuencia " + campos.secuencia()
									+ ": el evento fue cambiado, insertado o borrado fuera de la aplicación.");
							return;
						}
						anterior = guardado;
						cursor = campos.secuencia();
						revisados++;
					}
				}
			}
			while (leidos == LOTE);
		}
		registrar("hmac", true, "La cadena HMAC de los " + revisados + " eventos verifica con la clave.");
	}

	private void verificarConteos(Connection conexion, JsonNode manifiesto) throws SQLException {
		JsonNode conteos = manifiesto.get("conteos");
		if (conteos == null || !conteos.isObject() || conteos.isEmpty()) {
			registrar("conteos", false, "El manifiesto no trae los conteos de las tablas de solo inserción.");
			return;
		}
		List<String> faltan = new ArrayList<>();
		for (Map.Entry<String, JsonNode> conteo : conteos.properties()) {
			String tabla = conteo.getKey();
			if (!TABLA.matcher(tabla).matches()) {
				faltan.add("(nombre de tabla no válido)");
				continue;
			}
			long filasManifiesto = conteo.getValue().get(0).asLong();
			long idMaximo = conteo.getValue().get(1).asLong();
			try (PreparedStatement consulta = conexion.prepareStatement(
					"SELECT COUNT(*) FROM " + tabla + " WHERE id <= ?")) {
				consulta.setLong(1, idMaximo);
				try (ResultSet fila = consulta.executeQuery()) {
					fila.next();
					long filasCopia = fila.getLong(1);
					if (filasCopia < filasManifiesto) {
						faltan.add(tabla + " (" + (filasManifiesto - filasCopia) + ")");
					}
				}
			}
		}
		registrar("conteos", faltan.isEmpty(), faltan.isEmpty()
				? "Las " + conteos.size() + " tablas de solo inserción tienen todas las filas del manifiesto."
				: "Faltan filas que el manifiesto contó: " + String.join(", ", faltan) + ".");
	}

	/**
	 * Sprint 7, tanda 2 (H7, residual de {@code huellas_objetos()}): las huellas de los triggers y funciones leídas como
	 * ADMINISTRADOR directamente de {@code information_schema} (sin pasar por la función, que un DBA podría reemplazar) son
	 * las del jar. El archivo tiene una línea {@code nombre<TAB>huella} por objeto (restaurar-y-verificar.sh).
	 */
	void verificarObjetos(Path archivo) {
		Map<String, String> instaladas = new LinkedHashMap<>();
		try {
			for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
				String[] partes = linea.strip().split("\\s+");
				if (partes.length == 2) {
					instaladas.put(partes[0].toLowerCase(java.util.Locale.ROOT), partes[1]);
				}
			}
		}
		catch (IOException e) {
			registrar("objetos_bd", false, "No se pudo leer el archivo de huellas leído como administrador.");
			return;
		}
		Map<String, String> esperadas = HuellasObjetosBd.esperadas();
		List<String> distintos = new ArrayList<>();
		esperadas.forEach((nombre, huella) -> {
			if (!huella.equals(instaladas.get(nombre))) {
				distintos.add(nombre);
			}
		});
		instaladas.keySet().stream().filter(n -> !esperadas.containsKey(n)).forEach(distintos::add);
		registrar("objetos_bd", distintos.isEmpty(), distintos.isEmpty()
				? "Los " + esperadas.size() + " triggers y funciones (leídos como administrador) son los de esta versión."
				: "Triggers o funciones que faltan, sobran o cambiaron: " + String.join(", ", distintos) + ".");
	}

	private void verificarHuellas(Connection conexion) throws SQLException {
		long malas = 0;
		for (String tabla : List.of("huella_bitacora", "huella_hora")) {
			try (Statement sentencia = conexion.createStatement();
					ResultSet fila = sentencia.executeQuery("SELECT COUNT(*) FROM " + tabla + " h "
							+ "LEFT JOIN evento_auditoria e ON e.secuencia = h.secuencia AND e.colegio_id = h.colegio_id "
							+ "WHERE e.id IS NULL OR LEFT(e.hash, 16) <> h.codigo")) {
				fila.next();
				malas += fila.getLong(1);
			}
		}
		registrar("huellas", malas == 0, malas == 0 ? "Las huellas diarias y por hora coinciden con la bitácora."
				: malas + " huellas no coinciden con un evento de la bitácora: fue recortada o alterada.");
	}

	private void verificarLibros(Connection conexion) throws SQLException {
		String script;
		try (InputStream entrada = VerificadorRespaldo.class.getResourceAsStream(COMPROBACIONES)) {
			if (entrada == null) {
				registrar("libros", false, "El jar no trae " + COMPROBACIONES + ".");
				return;
			}
			script = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			registrar("libros", false, "No se pudo leer " + COMPROBACIONES + ": " + e.getMessage());
			return;
		}
		for (String consulta : sentencias(script)) {
			try (Statement sentencia = conexion.createStatement(); ResultSet fila = sentencia.executeQuery(consulta)) {
				fila.next();
				String control = fila.getString(1);
				long problemas = fila.getLong(2);
				registrar("libros_" + control, problemas == 0, problemas == 0 ? "Sin problemas."
						: problemas + " filas no cumplen «" + control + "».");
			}
		}
	}

	/** Las sentencias del archivo: sin comentarios de línea, separadas por «;» al final de una línea. */
	static List<String> sentencias(String script) {
		List<String> lista = new ArrayList<>();
		StringBuilder actual = new StringBuilder();
		for (String linea : script.split("\\R")) {
			String limpia = linea.strip();
			if (limpia.isEmpty() || limpia.startsWith("--")) {
				continue;
			}
			actual.append(linea).append('\n');
			if (limpia.endsWith(";")) {
				String sql = actual.toString().strip();
				lista.add(sql.substring(0, sql.length() - 1));
				actual.setLength(0);
			}
		}
		return lista;
	}

	private static long primerHueco(Connection conexion) throws SQLException {
		long esperada = 1;
		try (Statement sentencia = conexion.createStatement();
				ResultSet filas = sentencia.executeQuery("SELECT secuencia FROM evento_auditoria ORDER BY secuencia")) {
			while (filas.next()) {
				if (filas.getLong(1) != esperada) {
					return esperada;
				}
				esperada++;
			}
		}
		return esperada;
	}

	private static String hashDe(Connection conexion, long secuencia) throws SQLException {
		if (secuencia <= 0) {
			return null;
		}
		try (PreparedStatement consulta = conexion.prepareStatement(
				"SELECT hash FROM evento_auditoria WHERE secuencia = ?")) {
			consulta.setLong(1, secuencia);
			try (ResultSet fila = consulta.executeQuery()) {
				return fila.next() ? fila.getString(1) : null;
			}
		}
	}

	private void registrar(String nombre, boolean paso, String detalle) {
		resultados.add(new Comprobacion(nombre, paso, detalle));
		if (paso) {
			LOG.info("OK {}: {}", nombre, detalle);
		}
		else {
			LOG.error("FALLA {}: {}", nombre, detalle);
		}
	}

	private String informeJson(JsonNode manifiesto) {
		Map<String, Object> informe = new LinkedHashMap<>();
		informe.put("archivo", texto(manifiesto, "archivo"));
		informe.put("version_esquema", texto(manifiesto, "version_esquema"));
		informe.put("verificado", resultados.stream().allMatch(Comprobacion::paso));
		informe.put("comprobaciones", resultados);
		return JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(informe);
	}

	static JsonNode leerManifiesto(Path archivo) throws IOException {
		JsonNode manifiesto = JsonMapper.builder().build().readTree(Files.readString(archivo, StandardCharsets.UTF_8));
		for (String campo : List.of("archivo", "version_esquema", "secuencia_antes", "hash_antes", "secuencia_despues",
				"hash_despues", "conteos")) {
			if (manifiesto.get(campo) == null || manifiesto.get(campo).isNull()) {
				throw new IllegalArgumentException("al manifiesto " + archivo.getFileName() + " le falta «" + campo + "»");
			}
		}
		return manifiesto;
	}

	private static String texto(JsonNode nodo, String campo) {
		JsonNode valor = nodo.get(campo);
		return valor == null || valor.isNull() ? "" : valor.asString();
	}

	private static long numero(JsonNode nodo, String campo) {
		return nodo.get(campo).asLong();
	}

	/** Sin la configuración de Spring Boot, Logback registra en modo depuración: lo dejamos en INFO. */
	private static void silenciarDepuracion() {
		if (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) instanceof ch.qos.logback.classic.Logger raiz) {
			raiz.setLevel(Level.INFO);
		}
	}
}
