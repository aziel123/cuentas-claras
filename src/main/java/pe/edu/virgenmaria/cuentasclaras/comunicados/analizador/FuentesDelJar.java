package pe.edu.virgenmaria.cuentasclaras.comunicados.analizador;

import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Sprint 9, tanda 3 (B5): para extraer el texto de un PDF con fuentes no incrustadas, PDFBox buscaría las fuentes del
 * sistema (y guardaría su caché en el directorio del usuario). El hijo no lee nada fuera de su jar: toda fuente que falta
 * se sustituye por la Liberation Sans que trae PDFBox. Solo afecta al ancho de los caracteres, no al texto extraído.
 */
final class FuentesDelJar implements FontMapper {

	private static final String RECURSO = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

	private TrueTypeFont fuente;

	private synchronized TrueTypeFont fuente() {
		if (fuente == null) {
			try (InputStream entrada = FuentesDelJar.class.getResourceAsStream(RECURSO)) {
				if (entrada == null) {
					throw new IOException("Falta " + RECURSO);
				}
				fuente = new TTFParser().parse(new RandomAccessReadBuffer(entrada));
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return fuente;
	}

	@Override
	public FontMapping<TrueTypeFont> getTrueTypeFont(String nombre, PDFontDescriptor descriptor) {
		return new FontMapping<>(fuente(), true);
	}

	@Override
	public FontMapping<FontBoxFont> getFontBoxFont(String nombre, PDFontDescriptor descriptor) {
		return new FontMapping<>(fuente(), true);
	}

	@Override
	public CIDFontMapping getCIDFont(String nombre, PDFontDescriptor descriptor, PDCIDSystemInfo sistema) {
		return new CIDFontMapping(null, fuente(), true);
	}
}
