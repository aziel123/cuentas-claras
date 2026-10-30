package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sprint 11, tanda 2 (sección 3.9, AP-12): la lista fija {@code exportacion/tablas.txt} (empaquetada en el jar): qué tablas
 * van en la exportación de un colegio, cómo se filtran por su colegio (explícito o por el dueño) y qué columnas NO van.
 * Ninguna columna binaria va nunca (BLOB o VARBINARY): las libretas y los adjuntos van como PDF aparte.
 */
public final class TablasExportacion {

	/** Una tabla de la lista. */
	public record Tabla(String nombre, Filtro filtro, Set<String> sinColumnas) {

		/** El WHERE con un solo parámetro: el id del colegio. */
		public String where() {
			return switch (filtro) {
				case COLEGIO -> "colegio_id = ?";
				case USUARIO -> "usuario_id IN (SELECT u.id FROM usuario u WHERE u.colegio_id = ?)";
			};
		}
	}

	public enum Filtro {
		/** {@code WHERE colegio_id = ?}. */
		COLEGIO,
		/** Las filas de las cuentas del colegio ({@code usuario_rol}). */
		USUARIO
	}

	/** Nombres de tabla y columna: letras minúsculas, dígitos y guion bajo (van en el SQL). */
	static final Pattern NOMBRE = Pattern.compile("[a-z][a-z0-9_]{0,63}");

	/** Columnas que nunca van, en ninguna tabla (secretos y huellas de secretos). */
	public static final Set<String> NUNCA = Set.of("clave_hash", "hash_token", "token", "token_hash", "secreto_cifrado",
			"clave_cifrada", "codigo_escrito_sha256", "codigo_sha256", "huella_accion");

	private static final String RECURSO = "/exportacion/tablas.txt";

	private TablasExportacion() {
	}

	public static List<Tabla> cargar() {
		try (InputStream entrada = TablasExportacion.class.getResourceAsStream(RECURSO)) {
			Objects.requireNonNull(entrada, "Falta " + RECURSO);
			return leer(new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8)).lines().toList());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static List<Tabla> leer(List<String> lineas) {
		List<Tabla> tablas = new ArrayList<>();
		for (String linea : lineas) {
			String l = linea.strip();
			if (l.isEmpty() || l.startsWith("#")) {
				continue;
			}
			String[] partes = l.split("\\s+");
			String nombre = partes[0].toLowerCase(Locale.ROOT);
			if (!NOMBRE.matcher(nombre).matches() || partes.length < 2) {
				throw new IllegalStateException("tablas.txt: línea inválida «" + l + "»");
			}
			Filtro filtro = switch (partes[1]) {
				case "colegio_id" -> Filtro.COLEGIO;
				case "usuario" -> Filtro.USUARIO;
				default -> throw new IllegalStateException("tablas.txt: filtro desconocido en «" + l + "»");
			};
			Set<String> sin = new java.util.TreeSet<>();
			if (partes.length > 2) {
				for (String c : partes[2].split(",")) {
					String columna = c.strip().replaceFirst("^-", "");
					if (!NOMBRE.matcher(columna).matches()) {
						throw new IllegalStateException("tablas.txt: columna inválida en «" + l + "»");
					}
					sin.add(columna);
				}
			}
			tablas.add(new Tabla(nombre, filtro, Set.copyOf(sin)));
		}
		return List.copyOf(tablas);
	}
}
