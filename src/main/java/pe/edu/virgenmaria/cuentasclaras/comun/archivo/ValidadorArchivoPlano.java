package pe.edu.virgenmaria.cuentasclaras.comun.archivo;

import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Primera barrera ante un archivo de texto subido (CSV o TXT del banco), antes de leerlo. No guarda nada.
 * <ul>
 *   <li>Extensión {@code .csv} o {@code .txt}, no vacío y hasta 2 MB (el máximo que admite {@code archivo_cargado}).</li>
 *   <li>UTF-8 (con o sin BOM) o, si no es UTF-8 válido, ISO-8859-1 (lo que exportan muchos bancos).</li>
 *   <li>Sin bytes de control (salvo tabulador y saltos de línea): un archivo binario disfrazado de CSV se rechaza.</li>
 *   <li>Un máximo de líneas: un archivo enorme no llega al lector.</li>
 * </ul>
 * Puro: sin base de datos ni Spring.
 */
public final class ValidadorArchivoPlano {

	private ValidadorArchivoPlano() {
	}

	/** Si el nombre es de un archivo de texto plano ({@code .csv} o {@code .txt}). */
	public static boolean esTextoPlano(String nombre) {
		String minusculas = nombre == null ? "" : nombre.strip().toLowerCase(Locale.ROOT);
		return minusculas.endsWith(".csv") || minusculas.endsWith(".txt");
	}

	/**
	 * Valida y decodifica el archivo.
	 *
	 * @param maxLineas líneas como máximo (contando cabecera y pie)
	 * @return el texto, sin BOM
	 */
	public static String validar(String nombre, byte[] contenido, int maxLineas) {
		if (!esTextoPlano(nombre)) {
			throw new ArchivoNoValidoException("Sube el archivo del banco en CSV (.csv o .txt) o en Excel (.xlsx).");
		}
		if (contenido == null || contenido.length == 0) {
			throw new ArchivoNoValidoException("El archivo está vacío.");
		}
		exigirTamano(contenido.length);
		String texto = decodificar(contenido);
		long lineas = 1;
		for (int i = 0; i < texto.length(); i++) {
			char c = texto.charAt(i);
			if (c == '\n') {
				lineas++;
				if (lineas > maxLineas + 1L) {
					throw new ArchivoNoValidoException("El archivo tiene más de " + String.format("%,d", maxLineas)
							+ " líneas. Divídelo, por ejemplo un archivo por día.");
				}
			}
			else if (Character.isISOControl(c) && c != '\t' && c != '\r') {
				throw new ArchivoNoValidoException("El archivo tiene caracteres que no son de texto: no parece un CSV del "
						+ "banco. Descárgalo otra vez del portal del banco.");
			}
		}
		return texto;
	}

	/** El tamaño real del archivo subido (aunque solo se haya leído el comienzo). */
	public static void exigirTamano(long bytes) {
		if (bytes > ArchivoCargado.MAXIMO_BYTES) {
			throw new ArchivoNoValidoException("El archivo pesa más de 2 MB. Divídelo, por ejemplo un archivo por día.");
		}
	}

	private static String decodificar(byte[] contenido) {
		int inicio = contenido.length >= 3 && (contenido[0] & 0xFF) == 0xEF && (contenido[1] & 0xFF) == 0xBB
				&& (contenido[2] & 0xFF) == 0xBF ? 3 : 0;
		ByteBuffer bytes = ByteBuffer.wrap(contenido, inicio, contenido.length - inicio);
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(bytes).toString();
		}
		catch (CharacterCodingException noEsUtf8) {
			return new String(contenido, inicio, contenido.length - inicio, StandardCharsets.ISO_8859_1);
		}
	}
}
