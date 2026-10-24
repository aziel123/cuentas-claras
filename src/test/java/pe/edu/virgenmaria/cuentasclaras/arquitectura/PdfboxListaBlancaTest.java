package pe.edu.virgenmaria.cuentasclaras.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 3 (M11): la lista blanca de PDFBox ({@code ReglasArquitecturaTest.pdfboxEscribeSoloLaLibreta}) rechaza
 * cada prohibición expresa con su propia clase de prueba (imágenes desde archivos, unir PDF, importar páginas, formularios,
 * acciones, anotaciones y abrir un PDF) y deja pasar una clase que solo escribe.
 */
class PdfboxListaBlancaTest {

	private static final String PAQUETE = "pe.edu.virgenmaria.cuentasclaras.arquitectura.pdfbox";

	@ParameterizedTest
	@ValueSource(strings = { "ImagenDesdeArchivo", "UneDocumentos", "ImportaPagina", "AgregaFormulario", "AgregaAccion",
			"AgregaAnotacion", "AbreUnPdf" })
	void cadaProhibicionSeRechaza(String clase) throws ClassNotFoundException {
		JavaClasses importadas = new ClassFileImporter().importClasses(Class.forName(PAQUETE + "." + clase));
		assertThat(ListaBlancaPdfbox.regla(PAQUETE + "..").evaluate(importadas).hasViolation()).as(clase).isTrue();
	}

	@Test
	void unaClaseQueSoloEscribePasa() throws ClassNotFoundException {
		JavaClasses importadas = new ClassFileImporter().importClasses(Class.forName(PAQUETE
				+ ".PermitidaPorLaListaBlanca"));
		assertThat(ListaBlancaPdfbox.regla(PAQUETE + "..").evaluate(importadas).getFailureReport().getDetails()).isEmpty();
	}
}
