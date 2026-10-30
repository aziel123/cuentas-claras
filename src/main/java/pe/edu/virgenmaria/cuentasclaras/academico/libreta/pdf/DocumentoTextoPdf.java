package pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 11, tanda 2 (secciones 3.9 y 3.11): un documento de texto en PDF (la constancia {@code LEEME.pdf} de una
 * exportación y el acta de supresión para imprimir y firmar). Vive junto al generador de la libreta porque usa la misma
 * lista blanca de PDFBox (solo ESCRIBE: nunca abre un PDF ajeno) y la misma fuente. Párrafos cortos, A4, cortados por
 * ancho; sin imágenes.
 */
@Component
public class DocumentoTextoPdf {

	private static final float MARGEN = 56;

	private static final float TAMANO = 10;

	private static final float TITULO = 14;

	private static final float INTERLINEA = 14;

	/** @param parrafos cada uno se corta por ancho; un párrafo vacío deja una línea en blanco */
	public byte[] generar(String titulo, List<String> parrafos) {
		try (PDDocument documento = new PDDocument(MemoryUsageSetting.setupMainMemoryOnly().streamCache)) {
			PDFont fuente;
			try (InputStream ttf = DocumentoTextoPdf.class.getResourceAsStream(GeneradorLibretaPdf.FUENTE)) {
				if (ttf == null) {
					throw new IllegalStateException("Falta la fuente Liberation Sans de PDFBox");
				}
				fuente = PDType0Font.load(documento, ttf, true);
			}
			TextoSeguroPdf texto = new TextoSeguroPdf(fuente);
			float ancho = PDRectangle.A4.getWidth() - 2 * MARGEN;
			List<String> lineas = new ArrayList<>();
			for (String parrafo : parrafos) {
				lineas.addAll(cortar(texto.linea(parrafo == null ? "" : parrafo, 2000), fuente, ancho));
			}
			PDPage pagina = null;
			PDPageContentStream contenido = null;
			float y = 0;
			boolean primera = true;
			try {
				for (int i = -1; i < lineas.size(); i++) {
					if (pagina == null || y < MARGEN) {
						if (contenido != null) {
							contenido.close();
						}
						pagina = new PDPage(PDRectangle.A4);
						documento.addPage(pagina);
						contenido = new PDPageContentStream(documento, pagina);
						y = PDRectangle.A4.getHeight() - MARGEN;
					}
					if (i < 0) {
						escribir(contenido, fuente, TITULO, MARGEN, y, texto.linea(titulo, 120));
						y -= INTERLINEA * 2;
						primera = false;
						continue;
					}
					escribir(contenido, fuente, TAMANO, MARGEN, y, lineas.get(i));
					y -= INTERLINEA;
				}
			}
			finally {
				if (contenido != null) {
					contenido.close();
				}
			}
			PDDocumentInformation info = documento.getDocumentInformation();
			info.setTitle(texto.linea(titulo, 120));
			info.setProducer("Cuotaria");
			info.setCreator(primera ? "Cuotaria" : "Cuotaria");
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			documento.save(salida);
			return salida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar el documento", e);
		}
	}

	private static void escribir(PDPageContentStream contenido, PDFont fuente, float tamano, float x, float y, String linea)
			throws IOException {
		if (linea.isEmpty()) {
			return;
		}
		contenido.beginText();
		contenido.setFont(fuente, tamano);
		contenido.newLineAtOffset(x, y);
		contenido.showText(linea);
		contenido.endText();
	}

	private static List<String> cortar(String parrafo, PDFont fuente, float ancho) throws IOException {
		List<String> lineas = new ArrayList<>();
		if (parrafo.isBlank()) {
			lineas.add("");
			return lineas;
		}
		StringBuilder actual = new StringBuilder();
		for (String palabra : parrafo.split(" ")) {
			String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
			if (fuente.getStringWidth(prueba) / 1000 * TAMANO > ancho && !actual.isEmpty()) {
				lineas.add(actual.toString());
				actual = new StringBuilder(palabra);
			}
			else {
				actual = new StringBuilder(prueba);
			}
		}
		if (!actual.isEmpty()) {
			lineas.add(actual.toString());
		}
		return lineas;
	}
}
