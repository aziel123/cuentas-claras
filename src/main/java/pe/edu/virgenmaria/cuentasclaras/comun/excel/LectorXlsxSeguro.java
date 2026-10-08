package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.apache.poi.EmptyFileException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Lector de {@code .xlsx} endurecido, en modo streaming (SAX): nunca arma el libro completo en memoria.
 * <ul>
 *   <li>{@link ZipSecureFile}: ratio de compresión mínimo, tamaño máximo por entrada, cantidad de entradas y texto
 *       máximo de los textos compartidos (contra zip bombs).</li>
 *   <li>XML con {@link XMLHelper#newXMLReader()}: rechaza {@code DOCTYPE} (XXE) y entidades externas.</li>
 *   <li>Marca las celdas con fórmula ({@code <f>}) y no usa su valor; ignora la guía fonética ({@code <rPh>}).</li>
 *   <li>Lee el sistema de fechas del libro ({@code date1904}).</li>
 *   <li>Corta con error al pasar el máximo de filas.</li>
 * </ul>
 * Es la única clase (con {@link PlantillaXlsx} y {@link ValoresXlsx}) que importa Apache POI (regla ArchUnit).
 */
@Component
public class LectorXlsxSeguro {

	static final String MENSAJE_NO_PERMITIDO = "El archivo tiene contenido no permitido para una planilla de datos. "
			+ "Copia tus datos en la plantilla descargada y súbela otra vez.";

	private static final int MAX_LARGO_CELDA = 1000;

	private final int maxTextosCompartidos;

	public LectorXlsxSeguro(PropiedadesExcel propiedades) {
		this.maxTextosCompartidos = propiedades.maxTextosCompartidos();
		configurarZipSeguro(propiedades);
	}

	/** Valores globales de POI: se fijan una sola vez, al crear el lector. */
	static synchronized void configurarZipSeguro(PropiedadesExcel propiedades) {
		ZipSecureFile.setMinInflateRatio(0.01);
		ZipSecureFile.setMaxEntrySize(propiedades.maxDescomprimido().toBytes());
		ZipSecureFile.setMaxFileCount(propiedades.maxEntradasZip());
		ZipSecureFile.setMaxTextSize(5L * 1024 * 1024);
	}

	/** Lee la PRIMERA hoja del libro, sea cual sea su nombre (por ejemplo, un archivo exportado por el banco). */
	public HojaLeida leerPrimeraHoja(byte[] contenido, int maxFilas, int maxColumnas) {
		return leer(contenido, null, maxFilas, maxColumnas);
	}

	/**
	 * @param hoja        nombre exacto de la hoja que se lee ({@code null}: la primera)
	 * @param maxFilas    filas no vacías como máximo (incluida la de encabezados)
	 * @param maxColumnas columnas que se leen (desde A); las demás con datos se informan en {@code columnasIgnoradas}
	 */
	public HojaLeida leer(byte[] contenido, String hoja, int maxFilas, int maxColumnas) {
		try (OPCPackage paquete = OPCPackage.open(new ByteArrayInputStream(contenido))) {
			XSSFReader lector = new XSSFReader(paquete);
			boolean fecha1904 = esFecha1904(lector);
			exigirTextosCompartidos(paquete);
			ReadOnlySharedStringsTable textos = new ReadOnlySharedStringsTable(paquete, false);
			Iterator<InputStream> hojas = lector.getSheetsData();
			while (hojas.hasNext()) {
				try (InputStream datos = hojas.next()) {
					if (hoja == null
							|| hojas instanceof XSSFReader.SheetIterator iterador && hoja.equals(iterador.getSheetName())) {
						ManejadorHoja manejador = new ManejadorHoja(textos, maxFilas, maxColumnas);
						XMLReader xml = XMLHelper.newXMLReader();
						xml.setContentHandler(manejador);
						xml.parse(new InputSource(datos));
						return new HojaLeida(fecha1904, Collections.unmodifiableList(manejador.filas),
								Collections.unmodifiableSortedSet(manejador.ignoradas));
					}
				}
			}
			throw new ArchivoNoValidoException(hoja == null ? "El archivo no tiene hojas con datos."
					: "El archivo no tiene la hoja «" + hoja + "». Usa la plantilla descargada y no le cambies el nombre a "
							+ "la hoja.");
		}
		catch (ArchivoNoValidoException e) {
			throw e;
		}
		catch (DemasiadosTextosException e) {
			throw new ArchivoNoValidoException("El archivo tiene demasiados textos distintos (más de "
					+ String.format("%,d", maxTextosCompartidos) + "). Usa la plantilla descargada y copia solo los datos.");
		}
		catch (DemasiadasFilasException e) {
			throw new ArchivoNoValidoException("El archivo tiene más de " + String.format("%,d", maxFilas - 1)
					+ " filas de datos. Divídelo, por ejemplo un archivo por nivel.");
		}
		catch (OLE2NotOfficeXmlFileException e) {
			throw new ArchivoNoValidoException(ValidadorArchivoXlsx.MENSAJE_OLE2);
		}
		catch (NotOfficeXmlFileException | EmptyFileException | InvalidFormatException e) {
			throw new ArchivoNoValidoException(ValidadorArchivoXlsx.MENSAJE_NO_XLSX);
		}
		catch (SAXException e) {
			// DOCTYPE, entidades externas o XML mal formado.
			throw new ArchivoNoValidoException(MENSAJE_NO_PERMITIDO);
		}
		catch (IOException | OpenXML4JException | javax.xml.parsers.ParserConfigurationException | RuntimeException e) {
			// Incluye «Zip bomb detected!», archivos dañados e índices de textos inexistentes.
			throw new ArchivoNoValidoException(ValidadorArchivoXlsx.MENSAJE_NO_XLSX);
		}
	}

	private static boolean esFecha1904(XSSFReader lector)
			throws IOException, OpenXML4JException, SAXException, javax.xml.parsers.ParserConfigurationException {
		boolean[] fecha1904 = { false };
		try (InputStream libro = lector.getWorkbookData()) {
			XMLReader xml = XMLHelper.newXMLReader();
			xml.setContentHandler(new DefaultHandler() {
				@Override
				public void startElement(String uri, String local, String nombre, Attributes atributos) {
					if ("workbookPr".equals(local)) {
						String valor = atributos.getValue("date1904");
						fecha1904[0] = "1".equals(valor) || "true".equalsIgnoreCase(valor);
					}
				}
			});
			xml.parse(new InputSource(libro));
		}
		return fecha1904[0];
	}

	/** Se lanza desde el SAX para dejar de leer apenas se pasa el máximo de filas. */
	/**
	 * Auditoría B3: cuenta los textos compartidos ({@code <si>}) en streaming ANTES de cargarlos en memoria, y corta
	 * al pasar el máximo.
	 */
	private void exigirTextosCompartidos(OPCPackage paquete) throws IOException, SAXException,
			javax.xml.parsers.ParserConfigurationException {
		for (PackagePart parte : paquete.getPartsByContentType(XSSFRelation.SHARED_STRINGS.getContentType())) {
			try (InputStream datos = parte.getInputStream()) {
				XMLReader xml = XMLHelper.newXMLReader();
				xml.setContentHandler(new DefaultHandler() {

					private int textos;

					@Override
					public void startElement(String uri, String local, String nombre, Attributes atributos)
							throws SAXException {
						if (("si".equals(local) || "si".equals(nombre) || nombre.endsWith(":si"))
								&& ++textos > maxTextosCompartidos) {
							throw new DemasiadosTextosException();
						}
					}
				});
				xml.parse(new InputSource(datos));
			}
		}
	}

	private static final class DemasiadosTextosException extends SAXException {

		DemasiadosTextosException() {
			super("demasiados textos compartidos");
		}
	}

	private static final class DemasiadasFilasException extends SAXException {

		DemasiadasFilasException() {
			super("demasiadas filas");
		}
	}

	private static final class ManejadorHoja extends DefaultHandler {

		private final ReadOnlySharedStringsTable textos;

		private final int maxFilas;

		private final int maxColumnas;

		final List<FilaXlsx> filas = new ArrayList<>();

		final SortedSet<Integer> ignoradas = new TreeSet<>();

		private int numeroFila;

		private int ultimaFila;

		private Map<Integer, CeldaXlsx> celdas;

		private String tipo;

		private String referencia;

		private int columna;

		private int ultimaColumna;

		private boolean formula;

		private boolean enValor;

		private boolean enFonetica;

		private final StringBuilder valor = new StringBuilder();

		ManejadorHoja(ReadOnlySharedStringsTable textos, int maxFilas, int maxColumnas) {
			this.textos = textos;
			this.maxFilas = maxFilas;
			this.maxColumnas = maxColumnas;
		}

		@Override
		public void startElement(String uri, String local, String nombre, Attributes atributos) {
			switch (local) {
				case "row" -> {
					String r = atributos.getValue("r");
					numeroFila = r == null ? ultimaFila + 1 : Integer.parseInt(r);
					ultimaFila = numeroFila;
					ultimaColumna = -1;
					celdas = new HashMap<>();
				}
				case "c" -> {
					tipo = atributos.getValue("t");
					referencia = atributos.getValue("r");
					int indice = Columnas.indice(referencia);
					columna = indice >= 0 ? indice : ultimaColumna + 1;
					ultimaColumna = columna;
					if (referencia == null) {
						referencia = Columnas.letra(columna) + numeroFila;
					}
					formula = false;
					valor.setLength(0);
				}
				case "f" -> formula = true;
				case "rPh" -> enFonetica = true;
				case "v", "t" -> enValor = !enFonetica;
				default -> {
					// otros elementos de la hoja no interesan
				}
			}
		}

		@Override
		public void characters(char[] caracteres, int inicio, int largo) {
			if (enValor && valor.length() < MAX_LARGO_CELDA) {
				valor.append(caracteres, inicio, Math.min(largo, MAX_LARGO_CELDA - valor.length()));
			}
		}

		@Override
		public void endElement(String uri, String local, String nombre) throws SAXException {
			switch (local) {
				case "v", "t" -> enValor = false;
				case "rPh" -> enFonetica = false;
				case "c" -> terminarCelda();
				case "row" -> terminarFila();
				default -> {
					// nada
				}
			}
		}

		private void terminarCelda() {
			String crudo = valor.toString();
			TipoCelda tipoCelda;
			if ("s".equals(tipo)) {
				tipoCelda = TipoCelda.TEXTO;
				if (!crudo.isBlank()) {
					int indice = Integer.parseInt(crudo.strip());
					crudo = textos.getItemAt(indice).getString();
				}
			}
			else if ("inlineStr".equals(tipo) || "str".equals(tipo)) {
				tipoCelda = TipoCelda.TEXTO;
			}
			else if ("b".equals(tipo)) {
				tipoCelda = TipoCelda.BOOLEANO;
			}
			else if ("e".equals(tipo)) {
				tipoCelda = TipoCelda.ERROR;
			}
			else if ("d".equals(tipo)) {
				tipoCelda = TipoCelda.FECHA_ISO;
			}
			else {
				tipoCelda = TipoCelda.NUMERO;
			}
			if (crudo != null && crudo.length() > MAX_LARGO_CELDA) {
				crudo = crudo.substring(0, MAX_LARGO_CELDA);
			}
			CeldaXlsx celda = new CeldaXlsx(referencia, tipoCelda, crudo, formula);
			if (celda.vacia()) {
				return;
			}
			if (columna >= maxColumnas) {
				ignoradas.add(columna);
				return;
			}
			celdas.put(columna, celda);
		}

		private void terminarFila() throws SAXException {
			if (celdas.isEmpty()) {
				return;
			}
			if (filas.size() >= maxFilas) {
				throw new DemasiadasFilasException();
			}
			filas.add(new FilaXlsx(numeroFila, Map.copyOf(celdas)));
		}
	}
}
