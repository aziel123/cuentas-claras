package pe.edu.virgenmaria.cuentasclaras.arquitectura.pdfbox;

import org.apache.pdfbox.Loader;

import java.io.IOException;

/** Sprint 10, tanda 3 (M11): clase de prueba que usa {@code Loader}: la lista blanca de PDFBox debe rechazarla. */
class AbreUnPdf {

	Object abrir(byte[] pdf) throws IOException {
		return Loader.loadPDF(pdf);
	}
}
