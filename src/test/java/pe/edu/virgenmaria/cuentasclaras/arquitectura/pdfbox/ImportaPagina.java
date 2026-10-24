package pe.edu.virgenmaria.cuentasclaras.arquitectura.pdfbox;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.IOException;

/** Sprint 10, tanda 3 (M11): clase de prueba que usa {@code PDDocument.importPage}: la lista blanca de PDFBox debe rechazarla. */
class ImportaPagina {

	Object importar(PDDocument documento, PDPage ajena) throws IOException {
		return documento.importPage(ajena);
	}
}
