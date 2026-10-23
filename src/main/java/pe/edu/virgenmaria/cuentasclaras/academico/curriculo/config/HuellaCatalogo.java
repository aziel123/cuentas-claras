package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Sprint 10, tanda 1 (contrato de la sección 6 del brief): la huella de una versión del catálogo. SHA-256 (en hexadecimal)
 * de sus filas, en este orden y con un salto de línea ({@code CHAR(10)}) entre filas y la barra vertical entre columnas:
 * <ol>
 *   <li>las áreas por {@code (nivel, orden)}: {@code nivel|codigo|nombre|orden|transversal|exonerable};</li>
 *   <li>las competencias por {@code codigo}: {@code codigo|area|nombre|orden|solo_grados} (el área, por su código);</li>
 *   <li>la escala por {@code (nivel, orden)}: {@code nivel|valor|nombre|descripcion|orden|exige_conclusion}.</li>
 * </ol>
 * Los booleanos como {@code 0} o {@code 1}; NULL como cadena vacía. El orden lo pone Java (no la intercalación de la base),
 * así que da lo mismo en H2 y en MySQL. Los textos no tienen la barra ni saltos de línea (CHECK de V35, B13).
 */
public final class HuellaCatalogo {

	/** Una fila de {@code area_curricular}. */
	public record FilaArea(String nivel, String codigo, String nombre, int orden, boolean transversal, boolean exonerable) {
	}

	/** Una fila de {@code competencia}, con el código de su área. */
	public record FilaCompetencia(String codigo, String area, String nombre, int orden, String soloGrados) {
	}

	/** Una fila de {@code escala_logro}. */
	public record FilaEscala(String nivel, String valor, String nombre, String descripcion, int orden,
			boolean exigeConclusion) {
	}

	private HuellaCatalogo() {
	}

	/** La cadena exacta que se resume (pública para que una prueba la compare con la del contrato). */
	public static String cadena(List<FilaArea> areas, List<FilaCompetencia> competencias, List<FilaEscala> escala) {
		List<String> filas = new ArrayList<>();
		areas.stream().sorted(Comparator.comparing(FilaArea::nivel).thenComparingInt(FilaArea::orden))
				.forEach(a -> filas.add(String.join("|", a.nivel(), a.codigo(), a.nombre(), Integer.toString(a.orden()),
						bit(a.transversal()), bit(a.exonerable()))));
		competencias.stream().sorted(Comparator.comparing(FilaCompetencia::codigo))
				.forEach(c -> filas.add(String.join("|", c.codigo(), c.area(), c.nombre(), Integer.toString(c.orden()),
						c.soloGrados() == null ? "" : c.soloGrados())));
		escala.stream().sorted(Comparator.comparing(FilaEscala::nivel).thenComparingInt(FilaEscala::orden))
				.forEach(e -> filas.add(String.join("|", e.nivel(), e.valor(), e.nombre(), e.descripcion(),
						Integer.toString(e.orden()), bit(e.exigeConclusion()))));
		return String.join("\n", filas);
	}

	public static String de(List<FilaArea> areas, List<FilaCompetencia> competencias, List<FilaEscala> escala) {
		return sha256(cadena(areas, competencias, escala));
	}

	static String sha256(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("La JVM no tiene SHA-256", e);
		}
	}

	private static String bit(boolean valor) {
		return valor ? "1" : "0";
	}
}
