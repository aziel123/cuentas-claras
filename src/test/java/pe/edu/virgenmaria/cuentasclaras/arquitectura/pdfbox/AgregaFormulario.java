package pe.edu.virgenmaria.cuentasclaras.arquitectura.pdfbox;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;

/** Sprint 10, tanda 3 (M11): clase de prueba que usa {@code PDAcroForm}: la lista blanca de PDFBox debe rechazarla. */
class AgregaFormulario {

	Object formulario(PDDocument documento) {
		return new PDAcroForm(documento);
	}
}
