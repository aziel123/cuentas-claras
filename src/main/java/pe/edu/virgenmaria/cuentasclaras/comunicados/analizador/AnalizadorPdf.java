package pe.edu.virgenmaria.cuentasclaras.comunicados.analizador;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import java.awt.geom.Point2D;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * Sprint 9, tanda 3 (decisión 157, A9, A10, A14, B3, B5): el ÚNICO código de la aplicación que abre un PDF. Corre en un
 * proceso hijo ({@code LanzadorAnalizador}) con su propia memoria ({@code -Xmx128m}), su tiempo (el padre lo mata por su
 * PID a los 10 segundos), el entorno vacío (sin credenciales) y una política sin red ({@link PoliticaHijo}). No depende
 * de nada de la aplicación (regla ArchUnit): un fallo de PDFBox, un {@code StackOverflowError} o una bomba no tocan el
 * proceso que atiende a las personas.
 * <p>
 * Intercambio por archivos en {@code {dir}}: lee {@value #ENTRADA}; escribe {@value #SANEADO}, {@value #TEXTO} (hasta
 * 200 KB) y {@value #RESULTADO}. Salidas: {@value #ACEPTADO} aceptado, {@value #RECHAZADO} rechazado (con su motivo),
 * {@value #SIN_MEMORIA} sin memoria ({@code -XX:+ExitOnOutOfMemoryError}) y {@value #NO_REVISADO} no se pudo revisar.
 * <p>
 * Lo que revisa: hasta 2 MB, {@code %PDF-} en el byte 0 y nada de otro formato en el primer kilobyte (políglotos); se abre
 * con memoria acotada ({@code setupMixed(48 MB, 64 MB)}); cifrado o dañado, se rechaza; hasta 10 páginas; se RECHAZA (no
 * se limpia) todo lo activo: JavaScript, acciones automáticas ({@code AA}), {@code OpenAction} con acción, toda acción
 * que no sea {@code GoTo} o una {@code URI} de la lista permitida (así quedan fuera {@code Launch}, {@code SubmitForm},
 * {@code ImportData}, {@code ResetForm}, {@code Hide}, {@code Named}, {@code GoToR}, {@code GoToE}, {@code Rendition},
 * {@code Sound}, {@code Movie} y las que vengan), archivos embebidos, formularios ({@code AcroForm} y XFA), toda anotación
 * que no sea {@code Link} y las capas (OCG). Después se vuelve a guardar COMPLETO, sin {@code Info}, sin XMP y sin
 * revisiones, y el saneado se vuelve a abrir y revisar. {@code requiereRevision} (B3): menos de
 * {@value #MIN_CARACTERES_POR_PAGINA} caracteres extraíbles en alguna página, o imágenes.
 */
public final class AnalizadorPdf {

	public static final int ACEPTADO = 0;

	public static final int RECHAZADO = 2;

	public static final int SIN_MEMORIA = 3;

	public static final int NO_REVISADO = 4;

	public static final String ENTRADA = "entrada.pdf";

	public static final String SANEADO = "saneado.pdf";

	public static final String TEXTO = "texto.txt";

	public static final String RESULTADO = "resultado.properties";

	public static final long MAX_BYTES = 2L * 1024 * 1024;

	public static final int MAX_PAGINAS = 10;

	public static final int MAX_TEXTO = 200 * 1024;

	public static final int MIN_CARACTERES_POR_PAGINA = 50;

	static final int MAX_OBJETOS = 100_000;

	/** Un flujo (el contenido de una página, una fuente) descomprimido no pasa de 16 MB. */
	static final long MAX_FLUJO = 16L * 1024 * 1024;

	/** Todo el documento descomprimido no pasa de 48 MB (un PDF de 2 MB y 10 páginas de texto ocupa mucho menos). */
	static final long MAX_DESCOMPRIMIDO = 48L * 1024 * 1024;

	private static final Set<String> IMAGEN_CON_FORMATO = Set.of("DCTDecode", "DCT", "JPXDecode", "JBIG2Decode",
			"CCITTFaxDecode", "CCF");

	private static final long MEMORIA = 48L * 1024 * 1024;

	private static final long DISCO = 64L * 1024 * 1024;

	private static final Set<String> ACCIONES_PERMITIDAS = Set.of("GoTo", "URI");

	private static final COSName JS = COSName.getPDFName("JS");

	private static final COSName XFA = COSName.getPDFName("XFA");

	private static final COSName EMBEDDED_FILES = COSName.getPDFName("EmbeddedFiles");

	private static final COSName EF = COSName.getPDFName("EF");

	private static final COSName OC_PROPERTIES = COSName.getPDFName("OCProperties");

	private static final COSName OCG = COSName.getPDFName("OCG");

	private static final COSName PIECE_INFO = COSName.getPDFName("PieceInfo");

	private static final COSName ACCION = COSName.getPDFName("Action");

	private final Path directorio;

	private final Set<String> dominiosPermitidos;

	private boolean imagenes;

	AnalizadorPdf(Path directorio, Set<String> dominiosPermitidos) {
		this.directorio = directorio;
		this.dominiosPermitidos = dominiosPermitidos;
	}

	/** {@code java … AnalizadorPdf {dir} [dominio1,dominio2]}. */
	/** Correcciones del sprint 9 (auditoría, 5): el hijo se termina solo a los 12 s aunque el padre no lo mate. */
	static final long SEGUNDOS_TOPE = 12;

	public static void main(String[] argumentos) {
		Thread tope = new Thread(() -> {
			try {
				Thread.sleep(SEGUNDOS_TOPE * 1000);
			}
			catch (InterruptedException e) {
				return;
			}
			Runtime.getRuntime().halt(NO_REVISADO);
		}, "tope-del-analizador");
		tope.setDaemon(true);
		tope.start();
		int codigo;
		try {
			Path directorio = Path.of(argumentos[0]).toAbsolutePath().normalize();
			Set<String> dominios = argumentos.length > 1 ? Arrays.stream(argumentos[1].split(","))
					.map(d -> d.strip().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty() && !"-".equals(d))
					.collect(Collectors.toCollection(LinkedHashSet::new)) : Set.of();
			instalarPolitica(directorio);
			codigo = new AnalizadorPdf(directorio, dominios).analizar();
		}
		catch (OutOfMemoryError e) {
			codigo = SIN_MEMORIA;
		}
		catch (Throwable e) {
			codigo = NO_REVISADO;
		}
		System.exit(codigo);
	}

	/**
	 * Instala {@link PoliticaHijo}: lee el classpath (con el {@code Class-Path} de los manifiestos, como el del jar de
	 * Surefire), {@code java.home} y {@code dir}; escribe solo en {@code dir}; sin red. Pública para las pruebas del
	 * proceso hijo, que la instalan igual.
	 */
	@SuppressWarnings("removal")
	public static void instalarPolitica(Path directorio) {
		List<Path> lectura = new ArrayList<>();
		lectura.add(Path.of(System.getProperty("java.home")));
		for (String entrada : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
			if (!entrada.isBlank()) {
				Path ruta = Path.of(entrada).toAbsolutePath().normalize();
				lectura.add(ruta);
				lectura.addAll(delManifiesto(ruta));
			}
		}
		System.setSecurityManager(new PoliticaHijo(directorio, lectura));
	}

	private static List<Path> delManifiesto(Path jar) {
		if (!Files.isRegularFile(jar) || !jar.toString().endsWith(".jar")) {
			return List.of();
		}
		try (JarFile archivo = new JarFile(jar.toFile())) {
			Manifest manifiesto = archivo.getManifest();
			String rutas = manifiesto == null ? null : manifiesto.getMainAttributes().getValue("Class-Path");
			if (rutas == null) {
				return List.of();
			}
			List<Path> resultado = new ArrayList<>();
			for (String parte : rutas.trim().split("\\s+")) {
				try {
					URI uri = jar.toUri().resolve(parte);
					resultado.add("file".equals(uri.getScheme()) ? Path.of(uri) : Path.of(parte));
				}
				catch (IllegalArgumentException e) {
					// una entrada que no es una ruta: no se agrega
				}
			}
			return resultado;
		}
		catch (IOException e) {
			return List.of();
		}
	}

	int analizar() throws IOException {
		Path entrada = directorio.resolve(ENTRADA);
		long tamano = Files.size(entrada);
		if (tamano == 0 || tamano > MAX_BYTES) {
			return rechazar("pesa más de 2 MB o está vacío");
		}
		byte[] bytes = Files.readAllBytes(entrada);
		Optional<String> previo = antesDeAbrir(bytes);
		if (previo.isPresent()) {
			return rechazar(previo.get());
		}
		FontMappers.set(new FuentesDelJar());
		List<Integer> caracteres = new ArrayList<>();
		StringBuilder texto = new StringBuilder();
		int paginas;
		Path saneado = directorio.resolve(SANEADO);
		try (PDDocument documento = abrir(bytes)) {
			if (documento.isEncrypted()) {
				return rechazar("está protegido con contraseña o cifrado");
			}
			paginas = documento.getNumberOfPages();
			if (paginas < 1) {
				return rechazar("no tiene páginas");
			}
			if (paginas > MAX_PAGINAS) {
				return rechazar("tiene " + paginas + " páginas (máximo " + MAX_PAGINAS + ")");
			}
			Optional<String> activo = revisar(documento);
			if (activo.isPresent()) {
				return rechazar(activo.get());
			}
			ExtractorVisible extractor = new ExtractorVisible();
			for (int p = 1; p <= paginas; p++) {
				extractor.setStartPage(p);
				extractor.setEndPage(p);
				extractor.visibles = 0;
				String pagina = extractor.getText(documento);
				// Correcciones del sprint 9: el texto invisible (modo 3) no cuenta como texto de la página.
				caracteres.add(extractor.visibles);
				// Y una imagen en línea (BI … ID … EI) o dibujada desde un XObject cuenta como imagen.
				MotorDeImagenes motor = new MotorDeImagenes(documento.getPage(p - 1));
				motor.processPage(documento.getPage(p - 1));
				imagenes |= motor.dibujaImagen;
				if (texto.length() < MAX_TEXTO) {
					texto.append(pagina, 0, Math.min(pagina.length(), MAX_TEXTO - texto.length())).append('\n');
				}
			}
			sanear(documento);
			documento.save(saneado.toFile());
		}
		catch (InvalidPasswordException e) {
			return rechazar("está protegido con contraseña o cifrado");
		}
		catch (IOException | IllegalArgumentException | IllegalStateException | ClassCastException e) {
			return rechazar("está dañado o no se pudo leer como PDF");
		}
		catch (StackOverflowError e) {
			return rechazar("tiene una estructura demasiado profunda");
		}
		// El saneado se vuelve a abrir y a revisar: lo que se guarda en la base es ESTE archivo.
		byte[] limpio = Files.readAllBytes(saneado);
		if (limpio.length > MAX_BYTES) {
			return rechazar("al volver a guardarlo pesa más de 2 MB: expórtelo de nuevo con menos imágenes");
		}
		try (PDDocument otra = abrir(limpio)) {
			Optional<String> activo = revisar(otra);
			if (activo.isPresent() || otra.getNumberOfPages() != paginas || otra.isEncrypted()) {
				return rechazar(activo.orElse("cambió al volver a guardarlo"));
			}
		}
		catch (IOException | RuntimeException | StackOverflowError e) {
			return rechazar("no se pudo volver a abrir después de guardarlo");
		}
		Files.writeString(directorio.resolve(TEXTO), texto, StandardCharsets.UTF_8);
		boolean revision = imagenes || caracteres.stream().anyMatch(c -> c < MIN_CARACTERES_POR_PAGINA);
		Properties resultado = new Properties();
		resultado.setProperty("estado", "ACEPTADO");
		resultado.setProperty("paginas", Integer.toString(paginas));
		resultado.setProperty("caracteres", caracteres.stream().map(String::valueOf).collect(Collectors.joining(",")));
		resultado.setProperty("imagenes", Boolean.toString(imagenes));
		resultado.setProperty("requiereRevision", Boolean.toString(revision));
		escribir(resultado);
		return ACEPTADO;
	}

	/**
	 * Correcciones del sprint 9 (auditoría, 1): cuenta solo los caracteres que se ven. El texto en modo 3 (ni relleno ni
	 * trazo: invisible) va al texto que revisa el filtro, pero no cuenta para «tiene texto».
	 */
	static final class ExtractorVisible extends PDFTextStripper {

		int visibles;

		@Override
		protected void processTextPosition(TextPosition texto) {
			RenderingMode modo = getGraphicsState().getTextState().getRenderingMode();
			if (modo != RenderingMode.NEITHER && texto.getUnicode() != null) {
				visibles += (int) texto.getUnicode().codePoints().filter(c -> !Character.isWhitespace(c)).count();
			}
			super.processTextPosition(texto);
		}
	}

	/** Correcciones del sprint 9 (auditoría, 1): ¿la página dibuja alguna imagen (en línea o XObject)? */
	static final class MotorDeImagenes extends PDFGraphicsStreamEngine {

		boolean dibujaImagen;

		private final Point2D.Float punto = new Point2D.Float();

		MotorDeImagenes(PDPage pagina) {
			super(pagina);
		}

		@Override
		public void drawImage(PDImage imagen) {
			dibujaImagen = true;
		}

		@Override
		public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
		}

		@Override
		public void clip(int regla) {
		}

		@Override
		public void moveTo(float x, float y) {
			punto.setLocation(x, y);
		}

		@Override
		public void lineTo(float x, float y) {
			punto.setLocation(x, y);
		}

		@Override
		public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
			punto.setLocation(x3, y3);
		}

		@Override
		public Point2D getCurrentPoint() {
			return punto;
		}

		@Override
		public void closePath() {
		}

		@Override
		public void endPath() {
		}

		@Override
		public void strokePath() {
		}

		@Override
		public void fillPath(int regla) {
		}

		@Override
		public void fillAndStrokePath(int regla) {
		}

		@Override
		public void shadingFill(COSName nombre) {
		}
	}

	private static PDDocument abrir(byte[] bytes) throws IOException {
		return Loader.loadPDF(bytes, "", null, null, MemoryUsageSetting.setupMixed(MEMORIA, DISCO).streamCache);
	}

	/** Antes de abrirlo: el inicio del archivo (A9 y A14: bytes mágicos y políglotos en el primer kilobyte). */
	static Optional<String> antesDeAbrir(byte[] bytes) {
		if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
			return Optional.of("no es un PDF (no empieza con %PDF-)");
		}
		String inicio = new String(bytes, 0, Math.min(bytes.length, 1024), StandardCharsets.ISO_8859_1)
				.toLowerCase(Locale.ROOT);
		if (inicio.contains("pk\u0003\u0004") || inicio.contains("<html") || inicio.contains("<script")
				|| inicio.contains("<svg") || inicio.contains("<!doctype")) {
			return Optional.of("también es otro tipo de archivo (polígloto)");
		}
		return Optional.empty();
	}

	/**
	 * Recorre TODO lo alcanzable desde el trailer (sin recursión: una estructura profunda no desborda la pila) y devuelve
	 * el motivo del rechazo, si hay algo activo. De paso anota si hay imágenes.
	 */
	Optional<String> revisar(PDDocument documento) {
		Deque<COSBase> pendientes = new ArrayDeque<>();
		Set<COSBase> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
		pendientes.push(documento.getDocument().getTrailer());
		int objetos = 0;
		List<COSStream> flujos = new ArrayList<>();
		while (!pendientes.isEmpty()) {
			COSBase base = pendientes.pop();
			if (base instanceof COSObject referencia) {
				base = referencia.getObject();
			}
			if (base == null || !vistos.add(base)) {
				continue;
			}
			if (++objetos > MAX_OBJETOS) {
				return Optional.of("tiene demasiados objetos internos (posible archivo bomba)");
			}
			if (base instanceof COSDictionary diccionario) {
				Optional<String> motivo = revisarDiccionario(diccionario);
				if (motivo.isPresent()) {
					return motivo;
				}
				if (diccionario instanceof COSStream flujo) {
					flujos.add(flujo);
				}
				diccionario.getValues().forEach(pendientes::push);
			}
			else if (base instanceof COSArray arreglo) {
				arreglo.iterator().forEachRemaining(pendientes::push);
			}
		}
		return tamanoDecodificado(flujos);
	}

	/**
	 * Bomba de compresión (A9, B5): cuánto ocupa cada flujo DESCOMPRIMIDO, contado mientras se lee (sin guardarlo en
	 * memoria). Un flujo de más de {@value #MAX_FLUJO} bytes o un total de más de {@value #MAX_DESCOMPRIMIDO} se rechazan
	 * antes de extraer el texto o volver a guardar (el saneado no debe llevar la bomba a la familia). Las imágenes
	 * comprimidas con su propio formato (JPEG, JPEG 2000, JBIG2, CCITT) cuentan por su tamaño en el archivo.
	 */
	private static Optional<String> tamanoDecodificado(List<COSStream> flujos) {
		long total = 0;
		byte[] bufer = new byte[64 * 1024];
		for (COSStream flujo : flujos) {
			if (esImagenConSuFormato(flujo)) {
				total += flujo.getLength();
			}
			else {
				long este = 0;
				try (InputStream entrada = flujo.createInputStream()) {
					for (int n = entrada.read(bufer); n >= 0; n = entrada.read(bufer)) {
						este += n;
						if (este > MAX_FLUJO || total + este > MAX_DESCOMPRIMIDO) {
							return Optional.of("se descomprime a un tamaño desmedido (posible archivo bomba)");
						}
					}
				}
				catch (IOException e) {
					return Optional.of("está dañado o no se pudo leer como PDF");
				}
				total += este;
			}
			if (total > MAX_DESCOMPRIMIDO) {
				return Optional.of("se descomprime a un tamaño desmedido (posible archivo bomba)");
			}
		}
		return Optional.empty();
	}

	private static boolean esImagenConSuFormato(COSStream flujo) {
		COSBase filtro = flujo.getDictionaryObject(COSName.FILTER);
		List<COSBase> filtros = new ArrayList<>();
		if (filtro instanceof COSArray lista) {
			lista.forEach(filtros::add);
		}
		else if (filtro != null) {
			filtros.add(filtro);
		}
		return filtros.stream().anyMatch(f -> f instanceof COSName nombre && IMAGEN_CON_FORMATO.contains(nombre.getName()));
	}

	private Optional<String> revisarDiccionario(COSDictionary d) {
		if (d.containsKey(JS) || d.containsKey(COSName.JAVA_SCRIPT)) {
			return Optional.of("tiene JavaScript");
		}
		if (d.containsKey(COSName.AA)) {
			return Optional.of("tiene acciones automáticas");
		}
		if (d.containsKey(COSName.ACRO_FORM) || d.containsKey(XFA)) {
			return Optional.of("tiene un formulario (AcroForm o XFA)");
		}
		if (d.containsKey(EMBEDDED_FILES) || d.containsKey(EF)) {
			return Optional.of("tiene archivos adjuntos dentro");
		}
		if (d.containsKey(OC_PROPERTIES) || OCG.equals(d.getCOSName(COSName.TYPE)) || d.containsKey(COSName.OC)) {
			return Optional.of("tiene capas (contenido opcional)");
		}
		if (COSName.IMAGE.equals(d.getCOSName(COSName.SUBTYPE))) {
			imagenes = true;
		}
		if (d.containsKey(COSName.RECT) && d.getCOSName(COSName.SUBTYPE) != null
				&& !COSName.LINK.equals(d.getCOSName(COSName.SUBTYPE))) {
			return Optional.of("tiene anotaciones o campos que no son enlaces");
		}
		COSBase apertura = d.getDictionaryObject(COSName.OPEN_ACTION);
		if (apertura instanceof COSDictionary) {
			return Optional.of("ejecuta una acción al abrirse");
		}
		for (COSName clave : List.of(COSName.A, COSName.NEXT)) {
			Optional<String> motivo = revisarAcciones(d.getDictionaryObject(clave));
			if (motivo.isPresent()) {
				return motivo;
			}
		}
		if (ACCION.equals(d.getCOSName(COSName.TYPE))) {
			return revisarAccion(d);
		}
		return Optional.empty();
	}

	private Optional<String> revisarAcciones(COSBase valor) {
		if (valor instanceof COSDictionary accion) {
			return revisarAccion(accion);
		}
		if (valor instanceof COSArray lista) {
			for (COSBase elemento : lista) {
				COSBase resuelto = elemento instanceof COSObject o ? o.getObject() : elemento;
				if (resuelto instanceof COSDictionary accion) {
					Optional<String> motivo = revisarAccion(accion);
					if (motivo.isPresent()) {
						return motivo;
					}
				}
			}
		}
		return Optional.empty();
	}

	private Optional<String> revisarAccion(COSDictionary accion) {
		COSName tipo = accion.getCOSName(COSName.S);
		if (tipo == null || !ACCIONES_PERMITIDAS.contains(tipo.getName())) {
			return Optional.of("tiene una acción no permitida (" + (tipo == null ? "sin tipo" : tipo.getName()) + ")");
		}
		if ("URI".equals(tipo.getName())) {
			COSBase destino = accion.getDictionaryObject(COSName.URI);
			String texto = destino instanceof COSString s ? s.getString() : null;
			if (!enlacePermitido(texto)) {
				return Optional.of("tiene un enlace a un sitio fuera de la lista permitida");
			}
		}
		return Optional.empty();
	}

	/** Solo https (o http) a un dominio de la lista permitida (el de la plataforma y {@code dominios-permitidos}). */
	boolean enlacePermitido(String texto) {
		if (texto == null || texto.isBlank()) {
			return false;
		}
		try {
			URI uri = new URI(texto.strip());
			String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (!(esquema.equals("https") || esquema.equals("http")) || host.isEmpty()) {
				return false;
			}
			return dominiosPermitidos.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
		}
		catch (URISyntaxException e) {
			return false;
		}
	}

	/** Sin Info, sin XMP (en ningún diccionario), sin PieceInfo y sin un OpenAction que solo es un destino. */
	private static void sanear(PDDocument documento) {
		documento.getDocument().getTrailer().removeItem(COSName.INFO);
		COSDictionary catalogo = documento.getDocumentCatalog().getCOSObject();
		catalogo.removeItem(COSName.OPEN_ACTION);
		Deque<COSBase> pendientes = new ArrayDeque<>();
		Set<COSBase> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
		pendientes.push(catalogo);
		for (PDPage pagina : documento.getPages()) {
			pendientes.push(pagina.getCOSObject());
		}
		while (!pendientes.isEmpty()) {
			COSBase base = pendientes.pop();
			if (base instanceof COSObject referencia) {
				base = referencia.getObject();
			}
			if (base == null || !vistos.add(base)) {
				continue;
			}
			if (base instanceof COSDictionary d) {
				d.removeItem(COSName.METADATA);
				d.removeItem(PIECE_INFO);
				d.getValues().forEach(pendientes::push);
			}
			else if (base instanceof COSArray a) {
				a.iterator().forEachRemaining(pendientes::push);
			}
		}
	}

	private int rechazar(String motivo) throws IOException {
		Properties resultado = new Properties();
		resultado.setProperty("estado", "RECHAZADO");
		resultado.setProperty("motivo", motivo);
		escribir(resultado);
		return RECHAZADO;
	}

	private void escribir(Properties resultado) throws IOException {
		try (OutputStream salida = Files.newOutputStream(directorio.resolve(RESULTADO))) {
			resultado.store(new java.io.OutputStreamWriter(salida, StandardCharsets.UTF_8), null);
		}
	}

	/** Lee el resultado que dejó el hijo (para el padre), con un tope de tamaño. */
	public static Properties leerResultado(Path directorio, int maxBytes) throws IOException {
		Path archivo = directorio.resolve(RESULTADO);
		Properties resultado = new Properties();
		if (!Files.isRegularFile(archivo) || Files.size(archivo) > maxBytes) {
			return resultado;
		}
		try (InputStream entrada = Files.newInputStream(archivo)) {
			resultado.load(new java.io.InputStreamReader(entrada, StandardCharsets.UTF_8));
		}
		return resultado;
	}
}
