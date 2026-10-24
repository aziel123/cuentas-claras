package pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.ContenidoLibreta;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 10, tanda 3 (sección 3.9; E37, E38, E31, M9): el generador de la libreta con PDFBox (solo escritura). Texto hostil
 * (emoji, derecha a izquierda, {@code \u0000}, 1000 caracteres) no lo rompe; la libreta no habla de cobranza; el código de
 * estudiante va antes que el DNI; 45 libretas en menos de 10 s; con la cola llena responde «intente en unos minutos».
 * Con {@code -Dlibretas.muestras=true} deja las libretas de muestra en {@code docs/ux/libretas-muestra/}.
 */
class GeneradorLibretaPdfTest {

	private final GeneradorLibretaPdf generador = new GeneradorLibretaPdf(2, 8, 6, 45);

	@AfterEach
	void cerrar() {
		generador.cerrar();
	}

	private static String texto(byte[] pdf) throws IOException {
		try (PDDocument d = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(d);
		}
	}

	private static int paginas(byte[] pdf) throws IOException {
		try (PDDocument d = Loader.loadPDF(pdf)) {
			return d.getNumberOfPages();
		}
	}

	private byte[] una(ContenidoLibreta c, int version) {
		return generador.generar(List.of(new GeneradorLibretaPdf.Hoja(c, ContenidosDePrueba.version(version))), null);
	}

	/** E37: emoji, derecha a izquierda, el carácter nulo, formato invisible y 1000 caracteres: el PDF sale igual. */
	@Test
	void textoHostilNoRompeElPdf() throws IOException {
		for (String conclusion : List.of("Muy bien 😀👍 sigue así con la lectura en casa",
				"שלום עולם مرحبا بالعالم: lee en voz alta", "Lee\u0000 en‎ casa‮ todos­ los días",
				"x".repeat(1000))) {
			byte[] pdf = una(ContenidosDePrueba.conConclusion(ContenidosDePrueba.primaria(), conclusion), 1);
			String t = texto(pdf);
			assertThat(t).contains("Conclusión descriptiva");
			assertThat(paginas(pdf)).isLessThanOrEqualTo(6);
		}
		String conEmoji = texto(una(ContenidosDePrueba.conConclusion(ContenidosDePrueba.primaria(), "Muy bien 😀 sigue"), 1));
		assertThat(conEmoji).contains("Muy bien ? sigue").doesNotContain("😀");
		assertThat(texto(una(ContenidosDePrueba.conConclusion(ContenidosDePrueba.primaria(), "Lee\u0000 en casa todos "
				+ "los días"), 1))).contains("Lee en casa todos los días");
	}

	/** E31: la libreta no dice nada de pagos, deudas ni pensiones (la regla de cobranza de la base y de Java). */
	@Test
	void libretaSinCobranza() throws IOException {
		for (ContenidoLibreta c : List.of(ContenidosDePrueba.primaria(), ContenidosDePrueba.inicial())) {
			String t = texto(una(c, 2));
			assertThat(ContenidoSinCobranza.hablaDeCobranza(t)).as(t).isFalse();
			assertThat(t).doesNotContain("S/").doesNotContainIgnoringCase("deuda").doesNotContainIgnoringCase("pensión");
		}
	}

	/** M9: con código de estudiante va el código (no el DNI); sin código, el DNI con su tipo. */
	@Test
	void codigoAntesQueDni() throws IOException {
		String primaria = texto(una(ContenidosDePrueba.primaria(), 1));
		assertThat(primaria).contains("CÓDIGO DE ESTUDIANTE").contains("07123456789012").doesNotContain("DNI ");
		String inicial = texto(una(ContenidosDePrueba.inicial(), 1));
		assertThat(inicial).contains("DNI").contains("81234567");
	}

	/** A4: el código de verificación y la versión van en cada página; la versión 2 dice qué cambió. */
	@Test
	void codigoYVersionEnCadaPagina() throws IOException {
		byte[] pdf = una(ContenidosDePrueba.primaria(), 2);
		String t = texto(pdf);
		int paginas = paginas(pdf);
		assertThat(t.split("Código de verificación LIB-1234-1a2b3c4d5e6f7a8b · Emitida el 20/07/2026 · versión 2", -1))
				.hasSize(paginas + 1);
		assertThat(t).contains("Qué cambió: Matemática").contains("Situación al finalizar el periodo lectivo");
		// La misma libreta da el mismo texto (los metadatos llevan la fecha de emisión, no la de descarga).
		assertThat(texto(una(ContenidosDePrueba.primaria(), 2))).isEqualTo(t);
	}

	/** Sección 3.9: 45 libretas de una sección en un archivo, en menos de 10 s. */
	@Test
	void cuarentaYCincoLibretasEnMenosDeDiezSegundos() throws IOException {
		List<GeneradorLibretaPdf.Hoja> hojas = new ArrayList<>();
		for (int i = 0; i < 45; i++) {
			hojas.add(new GeneradorLibretaPdf.Hoja(ContenidosDePrueba.primaria(), ContenidosDePrueba.version(1)));
		}
		long inicio = System.nanoTime();
		byte[] pdf = generador.generar(hojas, null);
		long ms = (System.nanoTime() - inicio) / 1_000_000;
		assertThat(ms).as("45 libretas en milisegundos").isLessThan(10_000);
		assertThat(paginas(pdf)).isGreaterThanOrEqualTo(45);
		assertThatThrownBy(() -> {
			hojas.add(hojas.get(0));
			generador.generar(hojas, null);
		}).isInstanceOf(IllegalArgumentException.class);
	}

	/** E38: con los 2 hilos ocupados y la cola de 8 llena, la libreta siguiente responde «intente en unos minutos». */
	@Test
	void colaLlenaRespondeConMensaje() throws InterruptedException {
		CountDownLatch soltar = new CountDownLatch(1);
		for (int i = 0; i < 10; i++) {
			generador.ocupar(() -> {
				try {
					soltar.await(30, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}
		try {
			assertThatThrownBy(() -> una(ContenidosDePrueba.primaria(), 1))
					.isInstanceOf(GeneradorLibretaPdf.ColaLlenaException.class).hasMessageContaining("Intente en unos minutos");
		}
		finally {
			soltar.countDown();
		}
	}

	/** Las libretas de muestra para revisar el diseño (solo con -Dlibretas.muestras=true). */
	@Test
	void libretasDeMuestra() throws IOException {
		if (!Boolean.getBoolean("libretas.muestras")) {
			return;
		}
		Path carpeta = Path.of("docs", "ux", "libretas-muestra");
		Files.createDirectories(carpeta);
		Files.write(carpeta.resolve("libreta-primaria-5A-II-bimestre.pdf"), una(ContenidosDePrueba.primaria(), 2));
		Files.write(carpeta.resolve("libreta-inicial-5-anios-I-bimestre.pdf"), una(ContenidosDePrueba.inicial(), 1));
	}
}
