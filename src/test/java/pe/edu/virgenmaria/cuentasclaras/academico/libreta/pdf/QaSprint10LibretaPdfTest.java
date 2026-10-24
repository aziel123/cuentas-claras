package pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.TipoCodigo;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.ContenidoLibreta;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA del sprint 10 (sección 3.9 y riesgo 2 de la tanda 3): la maquetación de la libreta en los bordes que no cubría
 * {@link GeneradorLibretaPdfTest}: nombres con tildes y Ñ, nombres muy largos, conclusiones largas en todas las
 * competencias (saltos de página sin perder texto), Secundaria con sus 31 competencias, el logo ausente o dañado y el código
 * de verificación en cada página.
 */
class QaSprint10LibretaPdfTest {

	private static final String CODIGO = "LIB-98765-0f1e2d3c4b5a6978";

	private final GeneradorLibretaPdf generador = new GeneradorLibretaPdf(2, 8, 6, 45);

	@AfterEach
	void cerrar() {
		generador.cerrar();
	}

	@Test
	void debeEscribirTildesYEnieDelNombreSinCambiarlas() throws IOException {
		ContenidoLibreta c = conNombre(ContenidosDePrueba.primaria(), "Ñahui Peña, José Ángel Üriel");
		String t = texto(una(c, null));
		assertThat(t).contains("Ñahui Peña, José Ángel Üriel").doesNotContain("?ahui");
	}

	/**
	 * Un nombre de 190 caracteres (el máximo de la cabecera) no rompe la libreta: sale recortado con «…» en la fila de datos
	 * y en la cabecera de continuación (hallazgo QA-9: se recorta, no se ajusta en dos líneas).
	 */
	@Test
	void debeGenerarLaLibretaConUnNombreMuyLargo() throws IOException {
		String nombre = ("Apellidomuylargo De La Santísima Trinidad Fernández-Villavicencio Ortiz de Zevallos, "
				+ "María de los Ángeles Guadalupe Concepción del Rosario Inmaculada Esperanza Sofía Xiomara Yolanda Zoe "
				+ "Abigail Valentina").substring(0, 190);
		assertThat(nombre).hasSize(190);
		byte[] pdf = una(conNombre(ContenidosDePrueba.primaria(), nombre), null);
		String t = texto(pdf).replace("\r", "").replace("\n", " ");
		// Correcciones del sprint 10 (P-3): el nombre pasa a otra línea y sale COMPLETO (sin «…»).
		assertThat(t).contains("Apellidomuylargo De La Santísima Trinidad").contains(nombre.substring(150).strip())
				.doesNotContain(nombre.substring(0, 60) + "…");
		assertThat(paginas(pdf)).isBetween(1, 6);
	}

	/**
	 * Conclusiones de 1000 caracteres en TODAS las competencias de Primaria: la tabla salta de página, cada conclusión se
	 * imprime completa (su última palabra aparece) y el código de verificación está en cada página.
	 */
	@Test
	void debeSaltarDePaginaConConclusionesLargasSinPerderTexto() throws IOException {
		ContenidoLibreta c = conConclusionesLargas(ContenidosDePrueba.primaria(), 1000);
		byte[] pdf = una(c, null);
		String t = texto(pdf).replace("\n", " ").replace("\r", " ");
		int paginas = paginas(pdf);
		assertThat(paginas).isGreaterThan(1).isLessThanOrEqualTo(6);
		for (ContenidoLibreta.Area a : c.areas()) {
			for (ContenidoLibreta.Competencia k : a.competencias()) {
				if (k.delPeriodo().conclusion() != null) {
					assertThat(t).as("la conclusión de " + k.codigo() + " termina").contains("FIN" + k.codigo().replace("-", ""));
				}
			}
		}
		assertThat(t).doesNotContain("Libreta resumida");
		assertThat(conteo(t, "Código de verificación " + CODIGO)).isEqualTo(paginas);
		assertThat(t).contains("Página " + paginas + " de " + paginas);
	}

	/** Secundaria: 12 áreas y 31 competencias (con transversales), con conclusiones de 500 caracteres: no se recorta. */
	@Test
	void debeImprimirLas31CompetenciasDeSecundariaCompletas() throws IOException {
		ContenidoLibreta c = secundaria(500);
		byte[] pdf = una(c, null);
		String t = texto(pdf).replace("\n", " ").replace("\r", " ");
		int competencias = 0;
		for (ContenidoLibreta.Area a : c.areas()) {
			for (ContenidoLibreta.Competencia k : a.competencias()) {
				competencias++;
				assertThat(t).as(k.codigo()).contains("FIN" + k.codigo().replace("-", ""));
			}
		}
		assertThat(competencias).isEqualTo(31);
		assertThat(t).doesNotContain("Libreta resumida");
		assertThat(paginas(pdf)).isLessThanOrEqualTo(6);
	}

	/** Sin logo, con bytes que no son una imagen y con un PNG válido: la libreta sale siempre; con el PNG, lo dibuja. */
	@Test
	void debeGenerarLaLibretaConLogoAusenteDanadoOValido() throws IOException {
		ContenidoLibreta c = ContenidosDePrueba.primaria();
		byte[] sinLogo = una(c, null);
		byte[] danado = una(c, new byte[] { (byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3 });
		byte[] vacio = una(c, new byte[0]);
		byte[] conLogo = una(c, png());
		assertThat(imagenesPrimeraPagina(sinLogo)).isZero();
		assertThat(imagenesPrimeraPagina(danado)).isZero();
		assertThat(imagenesPrimeraPagina(vacio)).isZero();
		assertThat(imagenesPrimeraPagina(conLogo)).isEqualTo(1);
		// Sin logo va el monograma con las iniciales del colegio.
		assertThat(texto(sinLogo)).contains("VM");
	}

	/** El código de 16 caracteres de la huella y la versión, en el pie de cada página, con el formato del verificador. */
	@Test
	void debeImprimirElCodigoDeDieciseisCaracteresEnCadaPagina() throws IOException {
		byte[] pdf = una(conConclusionesLargas(ContenidosDePrueba.primaria(), 1000), null);
		String t = texto(pdf);
		Matcher m = Pattern.compile("Código de verificación (LIB-\\d+-[0-9a-f]{16}) · Emitida el \\d{2}/\\d{2}/\\d{4} · "
				+ "versión 1").matcher(t);
		int encontrados = 0;
		while (m.find()) {
			assertThat(m.group(1)).isEqualTo(CODIGO);
			encontrados++;
		}
		assertThat(encontrados).isEqualTo(paginas(pdf)).isGreaterThan(1);
	}

	// ------------------------------------------------------------------ contenidos

	private byte[] una(ContenidoLibreta c, byte[] logo) {
		GeneradorLibretaPdf.Version base = ContenidosDePrueba.version(1);
		GeneradorLibretaPdf.Version version = new GeneradorLibretaPdf.Version(CODIGO, 1, base.emitidaEn(), base.motivo(),
				null);
		return generador.generar(List.of(new GeneradorLibretaPdf.Hoja(c, version)), logo);
	}

	private static ContenidoLibreta conNombre(ContenidoLibreta c, String nombre) {
		LibretaEmitida.Cabecera k = c.cabecera();
		return new ContenidoLibreta(c.colegio(), c.nivel(), c.grado(), c.seccion(), c.anio(), c.alumnoId(), c.periodoId(),
				c.periodoNumero(), c.periodoNombre(), c.ultimoPeriodo(), new LibretaEmitida.Cabecera(nombre, k.alumnoCodigo(),
						k.tipoCodigo(), k.codigoModular(), k.dre(), k.ugel(), k.tutorNombre(), k.directorNombre()),
				c.periodos(), c.areas(), c.asistencia(), c.escala());
	}

	/** Cada competencia con nivel B y una conclusión de {@code largo} caracteres que termina en «FIN{código}». */
	private static ContenidoLibreta conConclusionesLargas(ContenidoLibreta c, int largo) {
		List<ContenidoLibreta.Area> areas = new ArrayList<>();
		for (ContenidoLibreta.Area a : c.areas()) {
			List<ContenidoLibreta.Competencia> comps = new ArrayList<>();
			for (ContenidoLibreta.Competencia k : a.competencias()) {
				List<ContenidoLibreta.Nota> notas = new ArrayList<>(k.notas());
				ContenidoLibreta.Nota n = notas.remove(notas.size() - 1);
				notas.add(new ContenidoLibreta.Nota(n.periodo(), "B", false, conclusion(k.codigo(), largo), "0".repeat(64)));
				comps.add(new ContenidoLibreta.Competencia(k.codigo(), k.nombre(), notas, k.anual()));
			}
			areas.add(new ContenidoLibreta.Area(a.codigo(), a.nombre(), a.transversal(), comps));
		}
		return new ContenidoLibreta(c.colegio(), c.nivel(), c.grado(), c.seccion(), c.anio(), c.alumnoId(), c.periodoId(),
				c.periodoNumero(), c.periodoNombre(), c.ultimoPeriodo(), c.cabecera(), c.periodos(), areas, c.asistencia(),
				c.escala());
	}

	/** Una conclusión realista (palabras con tildes) de {@code largo} caracteres que termina en «FIN{código sin guiones}». */
	private static String conclusion(String codigo, int largo) {
		String fin = " FIN" + codigo.replace("-", "");
		StringBuilder t = new StringBuilder();
		String[] palabras = { "Reconoce", "lo", "que", "aprendió", "en", "clase", "y", "necesita", "practicar", "con",
				"ejemplos", "de", "su", "vida", "diaria;", "se", "recomienda", "leer", "en", "casa", "acompañado." };
		int i = 0;
		while (t.length() + fin.length() < largo) {
			t.append(i == 0 ? "" : " ").append(palabras[i % palabras.length]);
			i++;
		}
		String cuerpo = t.substring(0, Math.max(0, Math.min(t.length(), largo - fin.length())));
		return cuerpo + fin;
	}

	/** 3.° de Secundaria, I bimestre: 11 áreas con 29 competencias y las 2 transversales (31, como el CNEB). */
	private static ContenidoLibreta secundaria(int largo) {
		String[][] areas = { { "DPCC", "2" }, { "CCSS", "3" }, { "EPT", "1" }, { "EF", "3" }, { "COM", "3" },
				{ "AYC", "2" }, { "ING", "3" }, { "MAT", "4" }, { "CYT", "3" }, { "REL", "2" }, { "C2L", "3" }, { "TRV", "2" } };
		List<ContenidoLibreta.Area> lista = new ArrayList<>();
		for (String[] a : areas) {
			List<ContenidoLibreta.Competencia> comps = new ArrayList<>();
			for (int i = 1; i <= Integer.parseInt(a[1]); i++) {
				String codigo = "SEC-" + a[0] + "-" + i;
				comps.add(new ContenidoLibreta.Competencia(codigo, "Competencia " + i + " del área " + a[0]
						+ " con un nombre largo como los del currículo nacional de educación básica",
						List.of(new ContenidoLibreta.Nota(1, "C", false, conclusion(codigo, largo), "0".repeat(64))), null));
			}
			lista.add(new ContenidoLibreta.Area(a[0], "Área " + a[0], a[0].equals("TRV"), comps));
		}
		// 29 de las áreas (con Castellano como segunda lengua) y las 2 transversales.
		return new ContenidoLibreta("Colegio Virgen María", "Secundaria", "3.° Secundaria", "B", 2026, 300L, 201L, 1,
				"I bimestre", false, new LibretaEmitida.Cabecera("Ccori Mamani, Ñusta Yuraq", "07999888777666",
						TipoCodigo.CODIGO_ESTUDIANTE, "0123458", "DRE Lima Metropolitana", "UGEL 03", "Tutor de Secundaria",
						"Carlos Mendoza Paredes"), List.of(new ContenidoLibreta.Periodo(1, "I bimestre")), lista,
				List.of(new ContenidoLibreta.Asistencia(1, "I bimestre", 0, 0, 0)), ContenidosDePrueba.ESCALA);
	}

	private static byte[] png() throws IOException {
		BufferedImage imagen = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagen.createGraphics();
		g.setColor(new Color(0x1b, 0x4f, 0x9c));
		g.fillRect(0, 0, 64, 64);
		g.dispose();
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(imagen, "png", salida);
		return salida.toByteArray();
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

	private static int imagenesPrimeraPagina(byte[] pdf) throws IOException {
		try (PDDocument d = Loader.loadPDF(pdf)) {
			PDPage pagina = d.getPage(0);
			int n = 0;
			for (COSName nombre : pagina.getResources().getXObjectNames()) {
				if (pagina.getResources().getXObject(nombre) instanceof PDImageXObject) {
					n++;
				}
			}
			return n;
		}
	}

	private static int conteo(String texto, String buscado) {
		int n = 0;
		for (int i = texto.indexOf(buscado); i >= 0; i = texto.indexOf(buscado, i + 1)) {
			n++;
		}
		return n;
	}
}
