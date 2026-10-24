package pe.edu.virgenmaria.cuentasclaras.arquitectura.pdfbox;

import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;

/** Sprint 10, tanda 3 (M11): clase de prueba que usa {@code interactive.action}: la lista blanca de PDFBox debe rechazarla. */
class AgregaAccion {

	Object accion() {
		return new PDActionJavaScript("app.alert('x')");
	}
}
