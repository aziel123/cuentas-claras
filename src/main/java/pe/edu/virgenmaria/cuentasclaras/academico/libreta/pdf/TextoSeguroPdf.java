package pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf;

import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprint 10, tanda 3 (sección 3.9, E37; riesgo 3): el texto que entra al PDF, saneado. Sin caracteres de control (salvo el
 * salto de línea) ni de formato ({@code \p{Cf}}: marcas de dirección, espacios de ancho cero, el guion suave), en forma
 * NFC, y todo carácter que la Liberation Sans del jar no tiene pasa a «?» (con un emoji o un texto de derecha a izquierda
 * PDFBox lanzaría una excepción y la familia se quedaría sin libreta). Con un largo máximo.
 */
public final class TextoSeguroPdf {

	private final PDFont fuente;

	private final Map<Integer, Boolean> tieneGlifo = new HashMap<>();

	public TextoSeguroPdf(PDFont fuente) {
		this.fuente = fuente;
	}

	/** El texto listo para {@code showText}: una sola línea (los saltos pasan a espacio). */
	public String linea(String texto, int maximo) {
		return limpiar(texto, maximo).replace('\n', ' ');
	}

	/** El texto saneado, conservando los saltos de línea (para ajustarlo a un ancho). */
	public String limpiar(String texto, int maximo) {
		if (texto == null || texto.isEmpty()) {
			return "";
		}
		String normal = Normalizer.normalize(texto, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n')
				.replace('\t', ' ');
		StringBuilder salida = new StringBuilder(Math.min(normal.length(), maximo));
		int[] puntos = normal.codePoints().toArray();
		for (int i = 0; i < puntos.length && salida.length() < maximo; i++) {
			int c = puntos[i];
			if (c == '\n') {
				salida.append('\n');
				continue;
			}
			int tipo = Character.getType(c);
			if (tipo == Character.CONTROL || tipo == Character.FORMAT || tipo == Character.SURROGATE
					|| tipo == Character.PRIVATE_USE || tipo == Character.UNASSIGNED) {
				continue;
			}
			if (Character.isSpaceChar(c)) {
				salida.append(' ');
				continue;
			}
			salida.append(tiene(c) ? new String(Character.toChars(c)) : "?");
		}
		return salida.toString();
	}

	private boolean tiene(int puntoDeCodigo) {
		return tieneGlifo.computeIfAbsent(puntoDeCodigo, c -> {
			try {
				fuente.encode(new String(Character.toChars(c)));
				return true;
			}
			catch (IllegalArgumentException | IOException e) {
				return false;
			}
		});
	}
}
