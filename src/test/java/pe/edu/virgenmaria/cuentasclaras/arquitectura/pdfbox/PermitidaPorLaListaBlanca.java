package pe.edu.virgenmaria.cuentasclaras.arquitectura.pdfbox;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Sprint 10, tanda 3 (M11): el control: una clase que solo escribe con la lista blanca no tiene violaciones. */
class PermitidaPorLaListaBlanca {

	byte[] escribir() throws IOException {
		try (PDDocument documento = new PDDocument()) {
			PDPage pagina = new PDPage(PDRectangle.A4);
			documento.addPage(pagina);
			try (PDPageContentStream lienzo = new PDPageContentStream(documento, pagina)) {
				lienzo.addRect(10, 10, 20, 20);
				lienzo.fill();
			}
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			documento.save(salida);
			return salida.toByteArray();
		}
	}
}
