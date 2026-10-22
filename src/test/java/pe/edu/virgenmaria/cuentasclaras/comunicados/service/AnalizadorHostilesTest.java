package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pe.edu.virgenmaria.cuentasclaras.comunicados.PdfsDePrueba;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (E26, A14, B3, B5): los PDF hostiles pasan por el proceso hijo y se RECHAZAN (no se limpian), con un
 * motivo claro; uno normal sale vuelto a guardar completo, sin Info, sin XMP y sin revisiones. Los de imagen o con poco
 * texto requieren revisión (B3). Cada caso lanza un proceso real.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalizadorHostilesTest {

	private static final Set<String> DOMINIOS = Set.of("cuentas.colegio.pe");

	@TempDir
	static Path base;

	private LanzadorAnalizador lanzador;

	@BeforeAll
	void preparar() throws Exception {
		Path dir = base != null ? base : java.nio.file.Files.createTempDirectory("cc-pdf-prueba");
		lanzador = new LanzadorAnalizador(dir.resolve("cc-pdf"), Duration.ofSeconds(10));
	}

	List<Object[]> hostiles() {
		return PdfsDePrueba.hostiles();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("hostiles")
	void cadaHostilSeRechazaConSuMotivo(String nombre, byte[] pdf) {
		LanzadorAnalizador.Salida salida = lanzador.analizar(pdf, DOMINIOS);
		assertThat(salida.aceptado()).as(nombre).isFalse();
		assertThat(salida.motivo()).as(nombre).isNotBlank();
		assertThat(salida.saneado()).as(nombre + ": no queda un saneado").isNull();
	}

	@Test
	void unoNormalSaleSaneadoYSinRevision() throws Exception {
		LanzadorAnalizador.Salida salida = lanzador.analizar(PdfsDePrueba.normal(), DOMINIOS);
		assertThat(salida.aceptado()).as(salida.motivo()).isTrue();
		assertThat(salida.resultado().getProperty("paginas")).isEqualTo("1");
		assertThat(AdjuntosComunicado.requiereRevision(salida)).isFalse();
		assertThat(salida.texto()).contains("Día del Logro");
		try (PDDocument saneado = Loader.loadPDF(salida.saneado())) {
			assertThat(saneado.getNumberOfPages()).isEqualTo(1);
		}
	}

	@Test
	void sinInfoNiXmpNiRevisiones() throws Exception {
		byte[] original = PdfsDePrueba.conInfoXmpYRevision();
		assertThat(contar(original, "%%EOF")).as("el original trae una revisión incremental").isGreaterThan(1);
		LanzadorAnalizador.Salida salida = lanzador.analizar(original, DOMINIOS);
		assertThat(salida.aceptado()).as(salida.motivo()).isTrue();
		assertThat(contar(salida.saneado(), "%%EOF")).as("guardado completo: una sola revisión").isEqualTo(1);
		try (PDDocument saneado = Loader.loadPDF(salida.saneado())) {
			assertThat(saneado.getDocument().getTrailer().containsKey(org.apache.pdfbox.cos.COSName.INFO)).isFalse();
			assertThat(saneado.getDocumentCatalog().getMetadata()).isNull();
			assertThat(saneado.getDocumentInformation().getTitle()).isNull();
		}
		assertThat(HuellaComunicado.sha256(salida.saneado())).isNotEqualTo(HuellaComunicado.sha256(original));
	}

	@Test
	void deImagenOConPocoTextoRequiereRevision() {
		LanzadorAnalizador.Salida imagen = lanzador.analizar(PdfsDePrueba.deImagen(), DOMINIOS);
		assertThat(imagen.aceptado()).as(imagen.motivo()).isTrue();
		assertThat(AdjuntosComunicado.requiereRevision(imagen)).isTrue();
		LanzadorAnalizador.Salida poco = lanzador.analizar(PdfsDePrueba.conPocoTexto(), DOMINIOS);
		assertThat(poco.aceptado()).as(poco.motivo()).isTrue();
		assertThat(AdjuntosComunicado.requiereRevision(poco)).isTrue();
	}

	@Test
	void unEnlaceALaPlataformaSePermite() {
		LanzadorAnalizador.Salida salida = lanzador.analizar(PdfsDePrueba.conEnlacePermitido(), DOMINIOS);
		assertThat(salida.aceptado()).as(salida.motivo()).isTrue();
	}

	@Test
	void losMotivosSonClaros() {
		assertThat(lanzador.analizar(PdfsDePrueba.conJavaScript(), DOMINIOS).motivo()).isNotBlank();
		assertThat(lanzador.analizar(PdfsDePrueba.cifrado(), DOMINIOS).motivo()).contains("contraseña");
		assertThat(lanzador.analizar(PdfsDePrueba.deOncePaginas(), DOMINIOS).motivo()).contains("11 páginas");
		assertThat(lanzador.analizar(PdfsDePrueba.extensionFalsa(), DOMINIOS).motivo()).contains("no es un PDF");
		assertThat(lanzador.analizar(PdfsDePrueba.conEnlaceAjeno(), DOMINIOS).motivo()).contains("enlace");
		assertThat(lanzador.analizar(PdfsDePrueba.conCapa(), DOMINIOS).motivo()).contains("capas");
		assertThat(lanzador.analizar(PdfsDePrueba.conAnotacionDeNota(), DOMINIOS).motivo()).contains("anotaciones");
	}

	private static int contar(byte[] bytes, String marca) {
		String texto = new String(bytes, StandardCharsets.ISO_8859_1);
		int cuenta = 0;
		for (int i = texto.indexOf(marca); i >= 0; i = texto.indexOf(marca, i + 1)) {
			cuenta++;
		}
		return cuenta;
	}
}
