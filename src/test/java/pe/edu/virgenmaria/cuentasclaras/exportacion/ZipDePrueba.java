package pe.edu.virgenmaria.cuentasclaras.exportacion;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.Exportador;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 11, tanda 2: abre el ZIP de una exportación con su clave (descifrada como lo hace la entrega) y lee cada CSV de
 * {@code datos/} fila por fila (RFC 4180: comillas, comas y saltos dentro de una celda).
 */
public final class ZipDePrueba {

	private ZipDePrueba() {
	}

	/** Por tabla: el encabezado y las filas. */
	static Map<String, List<List<String>>> csvs(JdbcTemplate jdbc, Exportador exportador, SecretosConectores secretos,
			long exportacionId) throws Exception {
		Map<String, Object> e = jdbc.queryForMap("SELECT * FROM exportacion_colegio WHERE id = ?", exportacionId);
		if (!"LISTA".equals(e.get("estado"))) {
			throw new IllegalStateException("La exportación no está LISTA: " + e.get("estado") + " " + e.get("detalle_fallo"));
		}
		String clave = clave(secretos, e);
		Path archivo = archivo(exportador, e);
		Map<String, List<List<String>>> tablas = new LinkedHashMap<>();
		try (ZipFile zip = new ZipFile(archivo.toFile(), clave.toCharArray())) {
			for (FileHeader h : zip.getFileHeaders()) {
				if (!h.getFileName().startsWith("datos/")) {
					continue;
				}
				try (InputStream in = zip.getInputStream(h)) {
					byte[] bytes = in.readAllBytes();
					String texto = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
					tablas.put(h.getFileName().substring(6, h.getFileName().length() - 4), leer(texto));
				}
			}
		}
		return tablas;
	}

	/** La clave del ZIP, como la descifra la entrega (sistema.exportacion). */
	public static String clave(SecretosConectores secretos, Map<String, Object> e) {
		long colegio = ((Number) e.get("colegio_id")).longValue();
		long id = ((Number) e.get("id")).longValue();
		return EjecucionComoSistema.como(ActorSistema.EXPORTACION, colegio, () -> secretos.descifrarClaveExportacion(
				(byte[]) e.get("clave_cifrada"), ((Number) e.get("clave_version")).intValue(), colegio, id));
	}

	/** Dónde está el ZIP. */
	public static Path archivo(Exportador exportador, Map<String, Object> e) {
		long colegio = ((Number) e.get("colegio_id")).longValue();
		return EjecucionComoSistema.como(ActorSistema.EXPORTACION, colegio, () -> exportador.directorio()
				.resolve((String) e.get("archivo")));
	}

	/** RFC 4180. */
	static List<List<String>> leer(String texto) {
		List<List<String>> filas = new ArrayList<>();
		List<String> fila = new ArrayList<>();
		StringBuilder celda = new StringBuilder();
		boolean comillas = false;
		for (int i = 0; i < texto.length(); i++) {
			char c = texto.charAt(i);
			if (comillas) {
				if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
					celda.append('"');
					i++;
				}
				else if (c == '"') {
					comillas = false;
				}
				else {
					celda.append(c);
				}
			}
			else if (c == '"') {
				comillas = true;
			}
			else if (c == ',') {
				fila.add(celda.toString());
				celda.setLength(0);
			}
			else if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
				fila.add(celda.toString());
				celda.setLength(0);
				filas.add(fila);
				fila = new ArrayList<>();
				i++;
			}
			else {
				celda.append(c);
			}
		}
		if (!fila.isEmpty() || celda.length() > 0) {
			fila.add(celda.toString());
			filas.add(fila);
		}
		return filas;
	}
}
