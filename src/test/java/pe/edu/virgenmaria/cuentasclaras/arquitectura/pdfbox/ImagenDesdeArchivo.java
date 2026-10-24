package pe.edu.virgenmaria.cuentasclaras.arquitectura.pdfbox;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.IOException;

/** Sprint 10, tanda 3 (M11): clase de prueba que usa {@code PDImageXObject.createFrom*}: la lista blanca de PDFBox debe rechazarla. */
class ImagenDesdeArchivo {

	Object dibujar(PDDocument documento) throws IOException {
		return PDImageXObject.createFromByteArray(documento, new byte[0], "logo");
	}
}
