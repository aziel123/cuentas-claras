package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Huellas de los triggers y las funciones de la base (sprint 7, tanda 2; sección 3.7, H7): las ESPERADAS salen de
 * {@code scripts/mysql/03-triggers.sql} y {@code 02-permisos-tablas.sql}, empaquetados en el jar ({@code db/mysql/}); las
 * INSTALADAS las devuelve {@code huellas_objetos()} (02). Las dos se calculan igual:
 * <ul>
 *   <li>trigger: {@code SHA-256(momento|evento|tabla|cuerpo normalizado)};</li>
 *   <li>función: {@code SHA-256(FUNCTION|cuerpo normalizado)};</li>
 *   <li>normalizar: sin los comentarios {@code --} hasta el fin de la línea, cada tramo de espacios en un espacio y
 *       recortado. Así da igual si el cliente {@code mysql} quitó los comentarios (8.0) o los dejó (8.4), o si el archivo
 *       tiene CRLF. Regla de {@code 03}: ningún literal dentro de un cuerpo contiene dos guiones seguidos
 *       ({@code HuellasObjetosBdTest}).</li>
 * </ul>
 * Una versión vieja o debilitada con el mismo nombre (por ejemplo, el {@code 03} del sprint 6), una función reemplazada o
 * un objeto de más cambian el resultado: prod no arranca.
 */
public final class HuellasObjetosBd {

	static final String TRIGGERS = "db/mysql/03-triggers.sql";

	static final String PERMISOS = "db/mysql/02-permisos-tablas.sql";

	private static final Pattern COMENTARIO = Pattern.compile("--[^\n]*");

	private static final Pattern ESPACIOS = Pattern.compile("\\s+");

	private static final Pattern TRIGGER = Pattern.compile("(?is)^CREATE\\s+TRIGGER\\s+(\\w+)\\s+(BEFORE|AFTER)\\s+"
			+ "(INSERT|UPDATE|DELETE)\\s+ON\\s+(\\w+)\\s+FOR\\s+EACH\\s+ROW(?:\\s+(?:FOLLOWS|PRECEDES)\\s+\\w+)?\\s+(.*)$");

	private static final Pattern FUNCION = Pattern.compile("(?is)^CREATE\\s+FUNCTION\\s+(?:\\w+\\.)?(\\w+)\\s*\\(.*?\\)\\s*"
			+ "RETURNS\\s+.*?\\s((?:BEGIN|RETURN)\\b.*)$");

	/** Un trigger o una función con su huella. */
	public record Objeto(String nombre, String tipo, String huella) {
	}

	private HuellasObjetosBd() {
	}

	/** {nombre: huella} de los triggers y funciones de 03 y de las funciones de 02, del jar. */
	public static Map<String, String> esperadas() {
		Map<String, String> huellas = new LinkedHashMap<>();
		for (Objeto o : objetos(leer(TRIGGERS))) {
			huellas.put(o.nombre(), o.huella());
		}
		for (Objeto o : objetos(leer(PERMISOS))) {
			huellas.put(o.nombre(), o.huella());
		}
		return huellas;
	}

	/** Los nombres de los triggers de 03, en su orden. */
	public static List<String> triggersEsperados() {
		return objetos(leer(TRIGGERS)).stream().filter(o -> "TRIGGER".equals(o.tipo())).map(Objeto::nombre).toList();
	}

	/** Los triggers y funciones de un script (con sus cambios de {@code DELIMITER}). */
	public static List<Objeto> objetos(String sql) {
		List<Objeto> objetos = new ArrayList<>();
		for (String sentencia : sentencias(sql)) {
			Matcher t = TRIGGER.matcher(sentencia);
			if (t.matches()) {
				objetos.add(new Objeto(t.group(1).toLowerCase(java.util.Locale.ROOT), "TRIGGER",
						sha256(String.join("|", t.group(2).toUpperCase(java.util.Locale.ROOT),
								t.group(3).toUpperCase(java.util.Locale.ROOT), t.group(4), normalizar(t.group(5))))));
				continue;
			}
			Matcher f = FUNCION.matcher(sentencia);
			if (f.matches()) {
				objetos.add(new Objeto(f.group(1).toLowerCase(java.util.Locale.ROOT), "FUNCTION",
						sha256("FUNCTION|" + normalizar(f.group(2)))));
			}
		}
		return objetos;
	}

	/**
	 * Igual que en {@code huellas_objetos()}: sin comentarios {@code --}, espacios colapsados y recortado. MySQL guarda el
	 * cuerpo con las secuencias de escape de sus literales ya procesadas (la barra y la «n» de un literal quedan como un
	 * salto de línea real; comprobado en MySQL 8.4.11): aquí se procesa igual antes de colapsar los espacios.
	 */
	public static String normalizar(String cuerpo) {
		String sinComentarios = COMENTARIO.matcher(cuerpo).replaceAll("");
		return ESPACIOS.matcher(sinComentarios.replace(ESCAPE_SALTO, "\n")).replaceAll(" ").trim();
	}

	/** La barra invertida seguida de «n» dentro de un literal SQL. */
	private static final String ESCAPE_SALTO = "\\" + "n";

	/** Las sentencias de un script, sin comentarios y respetando {@code DELIMITER}. */
	static List<String> sentencias(String sql) {
		String texto = COMENTARIO.matcher(sql.replace("\r\n", "\n")).replaceAll("");
		List<String> sentencias = new ArrayList<>();
		String delimitador = ";";
		StringBuilder actual = new StringBuilder();
		for (String linea : texto.split("\n", -1)) {
			String recortada = linea.trim();
			if (recortada.regionMatches(true, 0, "DELIMITER ", 0, 10)) {
				delimitador = recortada.substring(10).trim();
				continue;
			}
			actual.append(linea).append('\n');
			String acumulado = actual.toString().stripTrailing();
			if (acumulado.endsWith(delimitador)) {
				String sentencia = acumulado.substring(0, acumulado.length() - delimitador.length()).trim();
				if (!sentencia.isEmpty()) {
					sentencias.add(sentencia);
				}
				actual.setLength(0);
			}
		}
		return sentencias;
	}

	static String leer(String recurso) {
		try (InputStream entrada = new ClassPathResource(recurso).getInputStream()) {
			return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("No está " + recurso + " en el jar (recursos de Maven desde scripts/mysql)", e);
		}
	}

	static String sha256(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}
}
