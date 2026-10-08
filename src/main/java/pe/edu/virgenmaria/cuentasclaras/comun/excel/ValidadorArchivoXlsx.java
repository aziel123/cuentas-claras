package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Primera barrera ante un archivo subido, ANTES de dárselo a Apache POI. No guarda nada.
 * <ul>
 *   <li>Extensión {@code .xlsx}, tamaño máximo y firma de zip ({@code PK\3\4}). La firma OLE2 (archivo con
 *       contraseña o {@code .xls}) tiene su propio mensaje.</li>
 *   <li>Recorre el zip contando los bytes REALES descomprimidos (no los que declara el zip): corta al pasar el
 *       máximo (zip bomb) o la cantidad de entradas.</li>
 *   <li>Rechaza macros, vínculos externos, objetos incrustados y controles ActiveX, y entradas repetidas.</li>
 * </ul>
 */
@Component
public class ValidadorArchivoXlsx {

	private static final byte[] FIRMA_ZIP = { 'P', 'K', 3, 4 };

	private static final byte[] FIRMA_OLE2 = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1,
			0x1A, (byte) 0xE1 };

	static final String MENSAJE_OLE2 = "El archivo tiene contraseña o es .xls (Excel antiguo). Ábrelo en Excel y "
			+ "guárdalo como «Libro de Excel (.xlsx)» sin contraseña.";

	static final String MENSAJE_NO_XLSX = "El archivo no es un Excel .xlsx válido. Descarga la plantilla, copia tus "
			+ "datos en ella y súbela otra vez.";

	private final PropiedadesExcel propiedades;

	public ValidadorArchivoXlsx(PropiedadesExcel propiedades) {
		this.propiedades = propiedades;
	}

	public void validar(String nombre, byte[] contenido) {
		String nombreMinusculas = nombre == null ? "" : nombre.strip().toLowerCase(Locale.ROOT);
		if (nombreMinusculas.endsWith(".xlsm") || nombreMinusculas.endsWith(".xltm")) {
			throw new ArchivoNoValidoException("El archivo tiene macros (.xlsm). Guárdalo como «Libro de Excel (.xlsx)».");
		}
		if (nombreMinusculas.endsWith(".xls")) {
			throw new ArchivoNoValidoException(MENSAJE_OLE2);
		}
		if (!nombreMinusculas.endsWith(".xlsx")) {
			throw new ArchivoNoValidoException("Sube un archivo de Excel .xlsx (por ejemplo, la plantilla llena).");
		}
		if (contenido == null || contenido.length == 0) {
			throw new ArchivoNoValidoException("El archivo está vacío.");
		}
		exigirTamano(contenido.length);
		if (empiezaCon(contenido, FIRMA_OLE2)) {
			throw new ArchivoNoValidoException(MENSAJE_OLE2);
		}
		if (!empiezaCon(contenido, FIRMA_ZIP)) {
			throw new ArchivoNoValidoException(MENSAJE_NO_XLSX);
		}
		recorrerZip(contenido);
	}

	/** El tamaño real del archivo subido (aunque solo se haya leído el comienzo). */
	public void exigirTamano(long bytes) {
		long maximo = propiedades.maxBytes().toBytes();
		if (bytes > maximo) {
			throw new ArchivoNoValidoException("El archivo pesa " + megas(bytes) + " y el máximo es " + megas(maximo)
					+ ". Divídelo, por ejemplo un archivo por nivel.");
		}
	}

	private void recorrerZip(byte[] contenido) {
		long maximoDescomprimido = propiedades.maxDescomprimido().toBytes();
		long total = 0;
		int entradas = 0;
		Set<String> nombres = new HashSet<>();
		byte[] bufer = new byte[16 * 1024];
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
			ZipEntry entrada;
			while ((entrada = zip.getNextEntry()) != null) {
				entradas++;
				if (entradas > propiedades.maxEntradasZip()) {
					throw new ArchivoNoValidoException("El archivo tiene demasiadas partes internas para ser una "
							+ "planilla de alumnos. Copia tus datos en la plantilla.");
				}
				String parte = entrada.getName().toLowerCase(Locale.ROOT).replace('\\', '/');
				if (!nombres.add(parte)) {
					throw new ArchivoNoValidoException("El archivo está dañado (tiene partes repetidas). " + MENSAJE_NO_XLSX);
				}
				rechazarContenidoActivo(parte);
				int leidos;
				while ((leidos = zip.read(bufer)) != -1) {
					total += leidos;
					if (total > maximoDescomprimido) {
						throw new ArchivoNoValidoException("El archivo es demasiado grande al abrirlo (más de "
								+ megas(maximoDescomprimido) + "). No parece una planilla de alumnos.");
					}
				}
			}
		}
		catch (IOException | IllegalArgumentException e) {
			throw new ArchivoNoValidoException(MENSAJE_NO_XLSX);
		}
		if (entradas == 0 || !nombres.contains("[content_types].xml")) {
			throw new ArchivoNoValidoException(MENSAJE_NO_XLSX);
		}
	}

	private static void rechazarContenidoActivo(String parte) {
		if (parte.endsWith("vbaproject.bin") || parte.endsWith("vbadata.xml")) {
			throw new ArchivoNoValidoException("El archivo tiene macros. Guárdalo como «Libro de Excel (.xlsx)» "
					+ "sin macros.");
		}
		if (parte.startsWith("xl/externallinks/")) {
			throw new ArchivoNoValidoException("El archivo tiene vínculos a otros archivos. Copia solo los valores "
					+ "(Pegado especial → Valores) en la plantilla.");
		}
		if (parte.startsWith("xl/embeddings/") || parte.contains("activex")) {
			throw new ArchivoNoValidoException("El archivo tiene objetos o controles incrustados. Copia solo los "
					+ "datos en la plantilla.");
		}
	}

	private static boolean empiezaCon(byte[] contenido, byte[] firma) {
		if (contenido.length < firma.length) {
			return false;
		}
		for (int i = 0; i < firma.length; i++) {
			if (contenido[i] != firma[i]) {
				return false;
			}
		}
		return true;
	}

	static String megas(long bytes) {
		double megas = bytes / (1024.0 * 1024.0);
		return megas == Math.rint(megas) ? String.format(Locale.ROOT, "%.0f MB", megas)
				: String.format(Locale.ROOT, "%.1f MB", megas);
	}

	/** Lee como mucho {@code maximo + 1} bytes: para no cargar en memoria un archivo enorme. */
	public static byte[] leerConLimite(InputStream entrada, long maximo) throws IOException {
		return entrada.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maximo + 1));
	}
}
