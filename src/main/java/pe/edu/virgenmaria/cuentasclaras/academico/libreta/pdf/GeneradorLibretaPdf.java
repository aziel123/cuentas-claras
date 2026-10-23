package pe.edu.virgenmaria.cuentasclaras.academico.libreta.pdf;

import jakarta.annotation.PreDestroy;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.MotivoLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.ContenidoLibreta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.9; decisión 169, M11): la ÚNICA clase que escribe PDF con PDFBox (fuera del
 * analizador de comunicados, que solo lee en un proceso hijo). Solo la API de escritura, con la lista blanca de
 * {@code ReglasArquitecturaTest.pdfboxEscribeSoloLaLibreta}: documento nuevo, páginas A4, texto, rectángulos, líneas y el
 * logo PNG que el sprint 8 ya validó y reescaló (redibujado con {@link LosslessFactory}). Sin JavaScript, acciones,
 * formularios, anotaciones ni enlaces.
 * <ul>
 *   <li>Formato de los anexos 02 a 04 (supuesto S6): cabecera con la marca del colegio, datos del estudiante con el código
 *       de estudiante (el DNI solo si no hay código, M9), tabla por área y competencia con el nivel de cada periodo y la
 *       conclusión del periodo, transversales, asistencia, leyenda de la escala, firmas y, en cada página, el código de
 *       verificación {@code LIB-{id}-{16}} y «Emitida el … · versión N» (A4).</li>
 *   <li>Texto saneado ({@link TextoSeguroPdf}) con la Liberation Sans del jar (licencia SIL OFL), nunca una fuente del
 *       sistema.</li>
 *   <li>Límites: executor propio de {@code hilos} con cola de {@code cola} y {@code AbortPolicy} («intente en unos
 *       minutos», E38); memoria {@code setupMixed(32 MB, 64 MB)}; una libreta, {@code max-paginas} páginas como máximo.</li>
 *   <li>Metadatos mínimos: el título, «Cuotaria» como productor y la fecha de emisión (no la de descarga): la misma libreta
 *       da el mismo PDF.</li>
 * </ul>
 */
@Component
public class GeneradorLibretaPdf {

	private static final Logger LOG = LoggerFactory.getLogger(GeneradorLibretaPdf.class);

	static final String FUENTE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

	private static final long MEMORIA = 32L * 1024 * 1024;

	private static final long DISCO = 64L * 1024 * 1024;

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	// Marca del colegio (skill sistema-diseno): azul #1b4f9c, azul profundo #0f2c55, celeste #38aee6 (solo decorativo),
	// celeste suave #eaf6fd y texto #0f172a / #475569. Contraste AA del texto sobre cada fondo.
	private static final float[] AZUL = { 0x1b / 255f, 0x4f / 255f, 0x9c / 255f };

	private static final float[] AZUL_PROFUNDO = { 0x0f / 255f, 0x2c / 255f, 0x55 / 255f };

	private static final float[] CELESTE = { 0x38 / 255f, 0xae / 255f, 0xe6 / 255f };

	private static final float[] CELESTE_SUAVE = { 0xea / 255f, 0xf6 / 255f, 0xfd / 255f };

	private static final float[] SUPERFICIE = { 0xf3 / 255f, 0xf8 / 255f, 0xfd / 255f };

	private static final float[] BORDE = { 0xdc / 255f, 0xe7 / 255f, 0xf2 / 255f };

	private static final float[] TEXTO = { 0x0f / 255f, 0x17 / 255f, 0x2a / 255f };

	private static final float[] SECUNDARIO = { 0x47 / 255f, 0x55 / 255f, 0x69 / 255f };

	private static final float[] BLANCO = { 1f, 1f, 1f };

	private static final float[] ALERTA_SUAVE = { 0xfe / 255f, 0xf3 / 255f, 0xc7 / 255f };

	private static final float[] ALERTA = { 0xa1 / 255f, 0x4a / 255f, 0x06 / 255f };

	private static final float MARGEN = 40f;

	private static final float ANCHO = PDRectangle.A4.getWidth();

	private static final float ALTO = PDRectangle.A4.getHeight();

	private static final float PIE = 46f;

	/** Lo que se imprime de la versión (no entra en la huella del contenido). */
	public record Version(String codigo, int numero, LocalDateTime emitidaEn, MotivoLibreta motivo, String cambios) {

		public Version {
			Objects.requireNonNull(codigo, "codigo");
			Objects.requireNonNull(emitidaEn, "emitidaEn");
		}
	}

	/** Una libreta por imprimir: su contenido y su versión. */
	public record Hoja(ContenidoLibreta contenido, Version version) {
	}

	/** La cola del generador está llena (E38): la pantalla responde 503 con «intente en unos minutos». */
	public static class ColaLlenaException extends RuntimeException {

		public ColaLlenaException() {
			super("Se están generando muchas libretas en este momento. Intente en unos minutos.");
		}
	}

	private final ThreadPoolExecutor hilos;

	private final int maxPaginas;

	private final int maxPorArchivo;

	public GeneradorLibretaPdf(@Value("${cuentasclaras.libretas.hilos:2}") int hilos,
			@Value("${cuentasclaras.libretas.cola:8}") int cola,
			@Value("${cuentasclaras.libretas.max-paginas:6}") int maxPaginas,
			@Value("${cuentasclaras.libretas.max-alumnos-por-archivo:45}") int maxPorArchivo) {
		if (hilos < 1 || hilos > 2) {
			throw new IllegalStateException("cuentasclaras.libretas.hilos va de 1 a 2 (sección 3.9)");
		}
		AtomicInteger numero = new AtomicInteger();
		this.hilos = new ThreadPoolExecutor(hilos, hilos, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(cola),
				r -> {
					Thread t = new Thread(r, "libretas-pdf-" + numero.incrementAndGet());
					t.setDaemon(true);
					return t;
				}, new ThreadPoolExecutor.AbortPolicy());
		this.maxPaginas = maxPaginas;
		this.maxPorArchivo = maxPorArchivo;
	}

	@PreDestroy
	void cerrar() {
		hilos.shutdownNow();
	}

	public int maxPorArchivo() {
		return maxPorArchivo;
	}

	/**
	 * Genera el PDF de una o varias libretas (hasta {@code max-alumnos-por-archivo}), cada una desde una página nueva, en el
	 * executor propio. El logo es el PNG ya saneado del colegio (o {@code null}).
	 *
	 * @throws ColaLlenaException si la cola está llena (E38)
	 */
	public byte[] generar(List<Hoja> hojas, byte[] logoPng) {
		if (hojas.isEmpty() || hojas.size() > maxPorArchivo) {
			throw new IllegalArgumentException("De 1 a " + maxPorArchivo + " libretas por archivo");
		}
		Future<byte[]> tarea;
		try {
			tarea = hilos.submit(() -> escribir(hojas, logoPng));
		}
		catch (RejectedExecutionException e) {
			throw new ColaLlenaException();
		}
		try {
			return tarea.get(2, TimeUnit.MINUTES);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			tarea.cancel(true);
			throw new IllegalStateException("Se interrumpió la generación de la libreta", e);
		}
		catch (TimeoutException e) {
			tarea.cancel(true);
			throw new IllegalStateException("La generación de la libreta tardó demasiado", e);
		}
		catch (ExecutionException e) {
			throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
		}
	}

	/** Para las pruebas de la cola (E38): ocupa el executor como lo haría una libreta. */
	Future<?> ocupar(Runnable tarea) {
		try {
			return hilos.submit(tarea);
		}
		catch (RejectedExecutionException e) {
			throw new ColaLlenaException();
		}
	}

	byte[] escribir(List<Hoja> hojas, byte[] logoPng) {
		try (PDDocument documento = new PDDocument(MemoryUsageSetting.setupMixed(MEMORIA, DISCO).streamCache)) {
			PDFont fuente;
			try (InputStream ttf = GeneradorLibretaPdf.class.getResourceAsStream(FUENTE)) {
				if (ttf == null) {
					throw new IllegalStateException("Falta la fuente Liberation Sans de PDFBox");
				}
				fuente = PDType0Font.load(documento, ttf, true);
			}
			PDImageXObject logo = logo(documento, logoPng);
			TextoSeguroPdf texto = new TextoSeguroPdf(fuente);
			for (Hoja hoja : hojas) {
				new Maqueta(documento, fuente, texto, logo, hoja).escribir();
			}
			Hoja primera = hojas.get(0);
			PDDocumentInformation info = documento.getDocumentInformation();
			info.setTitle(texto.linea(hojas.size() == 1 ? "Libreta " + primera.version().codigo()
					: "Libretas de " + primera.contenido().grado() + " " + primera.contenido().seccion(), 120));
			info.setProducer("Cuotaria");
			info.setCreator("Cuotaria");
			Calendar emitida = GregorianCalendar.from(primera.version().emitidaEn().atZone(ZoneId.of("America/Lima")));
			emitida.setTimeZone(TimeZone.getTimeZone("America/Lima"));
			info.setCreationDate(emitida);
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			documento.save(salida);
			return salida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar la libreta", e);
		}
	}

	private static PDImageXObject logo(PDDocument documento, byte[] png) {
		if (png == null || png.length == 0) {
			return null;
		}
		try {
			BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
			return imagen == null ? null : LosslessFactory.createFromImage(documento, imagen);
		}
		catch (IOException | RuntimeException e) {
			LOG.warn("No se pudo dibujar el logo del colegio en la libreta: {}", e.getClass().getSimpleName());
			return null;
		}
	}

	/** La maquetación de UNA libreta: páginas, saltos de tabla y pie con el código en cada página. */
	private final class Maqueta {

		private final PDDocument documento;

		private final PDFont fuente;

		private final TextoSeguroPdf texto;

		private final PDImageXObject logo;

		private final ContenidoLibreta c;

		private final Version version;

		private final List<PDPage> paginas = new ArrayList<>();

		private PDPageContentStream lienzo;

		private float y;

		private boolean cortada;

		Maqueta(PDDocument documento, PDFont fuente, TextoSeguroPdf texto, PDImageXObject logo, Hoja hoja) {
			this.documento = documento;
			this.fuente = fuente;
			this.texto = texto;
			this.logo = logo;
			this.c = hoja.contenido();
			this.version = hoja.version();
		}

		void escribir() throws IOException {
			try {
				nuevaPagina();
				cabecera();
				if (version.numero() > 1) {
					aviso();
				}
				datos();
				List<ContenidoLibreta.Area> deAreas = c.areas().stream().filter(a -> !a.transversal()).toList();
				List<ContenidoLibreta.Area> transversales = c.areas().stream().filter(ContenidoLibreta.Area::transversal)
						.toList();
				tabla("Áreas curriculares", deAreas);
				if (!transversales.isEmpty()) {
					tabla("Competencias transversales", transversales);
				}
				asistencia();
				leyenda();
				cierre();
				lienzo.close();
			}
			catch (CorteDePaginas e) {
				cortada = true;
			}
			pies();
		}

		private void nuevaPagina() throws IOException {
			if (lienzo != null) {
				lienzo.close();
			}
			if (paginas.size() >= maxPaginas * 2) {
				throw new CorteDePaginas();
			}
			PDPage pagina = new PDPage(PDRectangle.A4);
			documento.addPage(pagina);
			paginas.add(pagina);
			lienzo = new PDPageContentStream(documento, pagina);
			// Franja celeste decorativa arriba (la marca) en todas las páginas.
			rect(0, ALTO - 6, ANCHO, 6, CELESTE);
			y = ALTO - 26;
			if (paginas.size() > 1) {
				escribirTexto(MARGEN, y, 8.5f, SECUNDARIO, c.cabecera().alumnoNombre() + " · " + c.grado() + " "
						+ c.seccion() + " · " + c.periodoNombre() + " " + c.anio() + " (continuación)", ANCHO - 2 * MARGEN);
				y -= 18;
			}
		}

		/** Si falta espacio para {@code alto}, pasa a otra página. */
		private void espacio(float alto) throws IOException {
			if (y - alto < PIE + 8) {
				if (paginas.size() >= maxPaginas) {
					LOG.warn("La libreta {} pasa de {} páginas", version.codigo(), maxPaginas);
				}
				nuevaPagina();
			}
		}

		private void cabecera() throws IOException {
			float alto = 64;
			rect(MARGEN, y - alto, ANCHO - 2 * MARGEN, alto, AZUL);
			rect(MARGEN, y - alto - 3, ANCHO - 2 * MARGEN, 3, CELESTE);
			float x = MARGEN + 12;
			if (logo != null) {
				float lado = 44;
				float escala = Math.min(lado / logo.getWidth(), lado / logo.getHeight());
				float w = logo.getWidth() * escala;
				float h = logo.getHeight() * escala;
				rect(x - 3, y - 10 - lado - 3, lado + 6, lado + 6, BLANCO);
				lienzo.drawImage(logo, x + (lado - w) / 2, y - 10 - lado + (lado - h) / 2, w, h);
				x += lado + 16;
			}
			else {
				// El monograma de la marca mientras no haya logo oficial.
				rect(x, y - 54, 44, 44, AZUL_PROFUNDO);
				rect(x, y - 54, 44, 4, CELESTE);
				escribirTexto(x + 9, y - 37, 16, BLANCO, iniciales(c.colegio()), 30);
				x += 60;
			}
			float ancho = ANCHO - MARGEN - 12 - x;
			escribirTexto(x, y - 24, 14, BLANCO, c.colegio(), ancho);
			escribirTexto(x, y - 40, 10, BLANCO, "Informe de progreso del aprendizaje · " + c.periodoNombre() + " · "
					+ c.anio(), ancho);
			escribirTexto(x, y - 54, 8.5f, BLANCO, "Educación Básica Regular · Nivel " + c.nivel(), ancho);
			y -= alto + 16;
		}

		private void aviso() throws IOException {
			String titulo = version.motivo() == MotivoLibreta.REEMISION ? "Versión " + version.numero() + ": "
					+ MotivoLibreta.TEXTO_REEMISION : "Versión " + version.numero() + ": reemplaza a la anterior por una "
							+ "corrección aprobada";
			List<String> lineas = envolver(version.cambios() == null ? "" : "Qué cambió: " + version.cambios(), 8.5f,
					ANCHO - 2 * MARGEN - 20);
			float alto = 22 + lineas.size() * 11f;
			espacio(alto + 8);
			rect(MARGEN, y - alto, ANCHO - 2 * MARGEN, alto, ALERTA_SUAVE);
			rect(MARGEN, y - alto, 3, alto, ALERTA);
			escribirTexto(MARGEN + 10, y - 14, 9.5f, ALERTA, titulo, ANCHO - 2 * MARGEN - 20);
			float yy = y - 27;
			for (String l : lineas) {
				escribirTexto(MARGEN + 10, yy, 8.5f, TEXTO, l, ANCHO - 2 * MARGEN - 20);
				yy -= 11;
			}
			y -= alto + 12;
		}

		private void datos() throws IOException {
			var k = c.cabecera();
			String[][] filas = {
					{ "DRE", valor(k.dre()), "UGEL", valor(k.ugel()) },
					{ "Código modular", valor(k.codigoModular()), "Nivel", c.nivel() },
					{ "Grado y sección", c.grado() + " " + c.seccion(), k.tipoCodigo().etiqueta(), k.alumnoCodigo() },
					{ "Apellidos y nombres", k.alumnoNombre(), "", "" },
					{ "Tutor(a)", valor(k.tutorNombre()), "", "" } };
			float alto = filas.length * 17f + 8;
			espacio(alto);
			rect(MARGEN, y - alto, ANCHO - 2 * MARGEN, alto, SUPERFICIE);
			float yy = y - 15;
			float col2 = MARGEN + (ANCHO - 2 * MARGEN) / 2;
			for (String[] f : filas) {
				boolean ancha = f[2].isEmpty();
				escribirTexto(MARGEN + 10, yy, 7.5f, SECUNDARIO, f[0].toUpperCase(java.util.Locale.ROOT), 105);
				escribirTexto(MARGEN + 118, yy, 9.5f, TEXTO, f[1], ancha ? ANCHO - 2 * MARGEN - 130 : col2 - MARGEN - 125);
				if (!ancha) {
					escribirTexto(col2 + 6, yy, 7.5f, SECUNDARIO, f[2].toUpperCase(java.util.Locale.ROOT), 95);
					escribirTexto(col2 + 104, yy, 9.5f, TEXTO, f[3], ANCHO - MARGEN - col2 - 112);
				}
				yy -= 17;
			}
			y -= alto + 14;
		}

		private void tabla(String titulo, List<ContenidoLibreta.Area> lista) throws IOException {
			if (lista.isEmpty()) {
				return;
			}
			int columnas = c.periodos().size() + (c.ultimoPeriodo() ? 1 : 0);
			float anchoNivel = 34;
			float anchoTotal = ANCHO - 2 * MARGEN;
			float anchoNombre = anchoTotal - columnas * anchoNivel;
			espacio(60);
			escribirTexto(MARGEN, y, 11, AZUL, titulo, anchoTotal);
			y -= 8;
			encabezadoTabla(anchoNombre, anchoNivel);
			for (ContenidoLibreta.Area area : lista) {
				espacio(40);
				rect(MARGEN, y - 16, anchoTotal, 16, CELESTE_SUAVE);
				escribirTexto(MARGEN + 6, y - 11.5f, 9.5f, AZUL_PROFUNDO, area.nombre(), anchoTotal - 12);
				y -= 16;
				for (ContenidoLibreta.Competencia comp : area.competencias()) {
					List<String> nombre = envolver(comp.nombre(), 8.5f, anchoNombre - 14);
					ContenidoLibreta.Nota actual = comp.delPeriodo();
					List<String> conclusion = actual.conclusion() == null ? List.of()
							: envolver("Conclusión descriptiva: " + actual.conclusion(), 7.5f, anchoTotal - 22);
					float altoFila = Math.max(16, nombre.size() * 10.5f + 6);
					float altoConclusion = conclusion.isEmpty() ? 0 : conclusion.size() * 9.5f + 6;
					if (y - altoFila - Math.min(altoConclusion, 40) < PIE + 8) {
						nuevaPagina();
						encabezadoTabla(anchoNombre, anchoNivel);
					}
					float yy = y - 11;
					for (String l : nombre) {
						escribirTexto(MARGEN + 12, yy, 8.5f, TEXTO, l, anchoNombre - 14);
						yy -= 10.5f;
					}
					float x = MARGEN + anchoNombre;
					for (ContenidoLibreta.Nota n : comp.notas()) {
						centrado(x, anchoNivel, y - 11, n.texto(), n.periodo() == c.periodoNumero());
						x += anchoNivel;
					}
					if (c.ultimoPeriodo()) {
						centrado(x, anchoNivel, y - 11, comp.anual() == null ? "—" : comp.anual(), true);
					}
					y -= altoFila;
					if (!conclusion.isEmpty()) {
						float yc = y - 8;
						for (String l : conclusion) {
							if (yc < PIE + 8) {
								nuevaPagina();
								yc = y - 8;
							}
							escribirTexto(MARGEN + 16, yc, 7.5f, SECUNDARIO, l, anchoTotal - 22);
							yc -= 9.5f;
						}
						y = yc + 3;
					}
					linea(MARGEN, y, MARGEN + anchoTotal, y, BORDE);
				}
			}
			y -= 14;
		}

		private void encabezadoTabla(float anchoNombre, float anchoNivel) throws IOException {
			float anchoTotal = ANCHO - 2 * MARGEN;
			rect(MARGEN, y - 18, anchoTotal, 18, AZUL);
			escribirTexto(MARGEN + 6, y - 12.5f, 8, BLANCO, "Competencia", anchoNombre - 8);
			float x = MARGEN + anchoNombre;
			for (ContenidoLibreta.Periodo p : c.periodos()) {
				centradoColor(x, anchoNivel, y - 12.5f, romano(p.numero()), BLANCO, 8);
				x += anchoNivel;
			}
			if (c.ultimoPeriodo()) {
				centradoColor(x, anchoNivel, y - 12.5f, "Anual", BLANCO, 8);
			}
			y -= 18;
		}

		private void asistencia() throws IOException {
			float anchoTotal = ANCHO - 2 * MARGEN;
			float alto = 34 + c.asistencia().size() * 15f;
			espacio(alto + 10);
			escribirTexto(MARGEN, y, 11, AZUL, "Asistencia", anchoTotal);
			y -= 8;
			String[] titulos = { "Periodo", "Inasistencias justificadas", "Inasistencias injustificadas", "Tardanzas" };
			float[] anchos = { anchoTotal * 0.28f, anchoTotal * 0.26f, anchoTotal * 0.26f, anchoTotal * 0.20f };
			rect(MARGEN, y - 18, anchoTotal, 18, AZUL);
			float x = MARGEN;
			for (int i = 0; i < titulos.length; i++) {
				escribirTexto(x + 6, y - 12.5f, 8, BLANCO, titulos[i], anchos[i] - 8);
				x += anchos[i];
			}
			y -= 18;
			for (ContenidoLibreta.Asistencia a : c.asistencia()) {
				String[] valores = { a.nombre(), String.valueOf(a.justificadas()), String.valueOf(a.injustificadas()),
						String.valueOf(a.tardanzas()) };
				x = MARGEN;
				for (int i = 0; i < valores.length; i++) {
					escribirTexto(x + 6, y - 11, 9, TEXTO, valores[i], anchos[i] - 8);
					x += anchos[i];
				}
				y -= 15;
				linea(MARGEN, y, MARGEN + anchoTotal, y, BORDE);
			}
			y -= 14;
		}

		private void leyenda() throws IOException {
			float anchoTotal = ANCHO - 2 * MARGEN;
			espacio(40);
			escribirTexto(MARGEN, y, 11, AZUL, "Escala de calificación", anchoTotal);
			y -= 14;
			for (ContenidoLibreta.Escala e : c.escala()) {
				List<String> lineas = envolver(e.nombre() + ": " + (e.descripcion() == null ? "" : e.descripcion()), 7.5f,
						anchoTotal - 40);
				espacio(lineas.size() * 9.5f + 4);
				escribirTexto(MARGEN, y, 9, AZUL_PROFUNDO, e.valor(), 30);
				for (String l : lineas) {
					escribirTexto(MARGEN + 30, y, 7.5f, SECUNDARIO, l, anchoTotal - 40);
					y -= 9.5f;
				}
				y -= 3;
			}
			escribirTexto(MARGEN, y, 7.5f, SECUNDARIO, "EXO: exonerado del área. —: sin nota en el periodo.", anchoTotal);
			y -= 16;
		}

		private void cierre() throws IOException {
			float anchoTotal = ANCHO - 2 * MARGEN;
			espacio(80);
			escribirTexto(MARGEN, y, 9, TEXTO, "Situación al finalizar el periodo lectivo: se informa al cierre del año.",
					anchoTotal);
			y -= 46;
			float mitad = anchoTotal / 2;
			linea(MARGEN + 10, y, MARGEN + mitad - 20, y, SECUNDARIO);
			linea(MARGEN + mitad + 20, y, MARGEN + anchoTotal - 10, y, SECUNDARIO);
			escribirTexto(MARGEN + 10, y - 11, 8.5f, TEXTO, valor(c.cabecera().tutorNombre()), mitad - 30);
			escribirTexto(MARGEN + 10, y - 21, 7.5f, SECUNDARIO, "Tutor(a)", mitad - 30);
			escribirTexto(MARGEN + mitad + 20, y - 11, 8.5f, TEXTO, c.cabecera().directorNombre(), mitad - 30);
			escribirTexto(MARGEN + mitad + 20, y - 21, 7.5f, SECUNDARIO, "Dirección (cerró el periodo)", mitad - 30);
			y -= 32;
		}

		/** En cada página: el código de verificación, la emisión y la versión, y «Página x de y». */
		private void pies() throws IOException {
			int total = paginas.size();
			for (int i = 0; i < total; i++) {
				try (PDPageContentStream pie = new PDPageContentStream(documento, paginas.get(i),
						PDPageContentStream.AppendMode.APPEND, true)) {
					PDPageContentStream antes = lienzo;
					lienzo = pie;
					linea(MARGEN, PIE - 6, ANCHO - MARGEN, PIE - 6, BORDE);
					escribirTexto(MARGEN, PIE - 18, 7.5f, SECUNDARIO, "Código de verificación " + version.codigo()
							+ " · Emitida el " + version.emitidaEn().format(FECHA) + " · versión " + version.numero(),
							ANCHO - 2 * MARGEN - 70);
					escribirTexto(ANCHO - MARGEN - 62, PIE - 18, 7.5f, SECUNDARIO, "Página " + (i + 1) + " de " + total, 62);
					escribirTexto(MARGEN, PIE - 29, 6.5f, SECUNDARIO, "Verifíquela en el colegio con este código. "
							+ (cortada ? "Libreta resumida por su extensión. " : "") + "Generada por Cuotaria.",
							ANCHO - 2 * MARGEN);
					lienzo = antes;
				}
			}
		}

		// ---------------------------------------------------------------------------------------------- dibujo

		private void escribirTexto(float x, float yy, float tamano, float[] color, String valor, float ancho)
				throws IOException {
			String limpio = recortar(texto.linea(valor, 400), tamano, ancho);
			if (limpio.isEmpty()) {
				return;
			}
			lienzo.beginText();
			lienzo.setFont(fuente, tamano);
			lienzo.setNonStrokingColor(color[0], color[1], color[2]);
			lienzo.newLineAtOffset(x, yy);
			lienzo.showText(limpio);
			lienzo.endText();
		}

		private void centrado(float x, float ancho, float yy, String valor, boolean destacado) throws IOException {
			if (destacado) {
				rect(x + 4, yy - 4, ancho - 8, 13, SUPERFICIE);
			}
			centradoColor(x, ancho, yy, valor, destacado ? AZUL_PROFUNDO : TEXTO, destacado ? 9.5f : 8.5f);
		}

		private void centradoColor(float x, float ancho, float yy, String valor, float[] color, float tamano)
				throws IOException {
			String limpio = texto.linea(valor, 20);
			float w = fuente.getStringWidth(limpio) / 1000 * tamano;
			escribirTexto(x + (ancho - w) / 2, yy, tamano, color, limpio, ancho);
		}

		private void rect(float x, float yy, float w, float h, float[] color) throws IOException {
			lienzo.setNonStrokingColor(color[0], color[1], color[2]);
			lienzo.addRect(x, yy, w, h);
			lienzo.fill();
		}

		private void linea(float x1, float y1, float x2, float y2, float[] color) throws IOException {
			lienzo.setStrokingColor(color[0], color[1], color[2]);
			lienzo.setLineWidth(0.6f);
			lienzo.moveTo(x1, y1);
			lienzo.lineTo(x2, y2);
			lienzo.stroke();
		}

		/** Ajusta un texto al ancho, por palabras (una palabra más larga que el ancho se corta). */
		private List<String> envolver(String valor, float tamano, float ancho) throws IOException {
			List<String> lineas = new ArrayList<>();
			for (String parrafo : texto.limpiar(valor, 1200).split("\n")) {
				StringBuilder actual = new StringBuilder();
				for (String palabra : parrafo.split(" ")) {
					if (palabra.isEmpty()) {
						continue;
					}
					String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
					if (medir(prueba, tamano) <= ancho) {
						actual.setLength(0);
						actual.append(prueba);
						continue;
					}
					if (!actual.isEmpty()) {
						lineas.add(actual.toString());
						actual.setLength(0);
					}
					String resto = palabra;
					while (medir(resto, tamano) > ancho && resto.length() > 1) {
						int corte = resto.length() - 1;
						while (corte > 1 && medir(resto.substring(0, corte), tamano) > ancho) {
							corte--;
						}
						lineas.add(resto.substring(0, corte));
						resto = resto.substring(corte);
					}
					actual.append(resto);
				}
				if (!actual.isEmpty()) {
					lineas.add(actual.toString());
				}
			}
			return lineas;
		}

		private float medir(String valor, float tamano) throws IOException {
			return fuente.getStringWidth(valor) / 1000 * tamano;
		}

		private String recortar(String valor, float tamano, float ancho) throws IOException {
			if (medir(valor, tamano) <= ancho) {
				return valor;
			}
			String v = valor;
			while (v.length() > 1 && medir(v + "…", tamano) > ancho) {
				v = v.substring(0, v.length() - 1);
			}
			return v + "…";
		}
	}

	/** Se pasó del tope de páginas de una libreta: se corta (nunca deja a la familia sin libreta). */
	private static final class CorteDePaginas extends IOException {
	}

	private static String valor(String v) {
		return v == null || v.isBlank() ? "—" : v;
	}

	static String romano(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> String.valueOf(n);
		};
	}

	private static String iniciales(String colegio) {
		StringBuilder s = new StringBuilder();
		for (String p : colegio.split("\\s+")) {
			if (!p.isEmpty() && Character.isUpperCase(p.charAt(0)) && s.length() < 2) {
				s.append(p.charAt(0));
			}
		}
		return s.isEmpty() ? "VM" : s.toString();
	}
}
