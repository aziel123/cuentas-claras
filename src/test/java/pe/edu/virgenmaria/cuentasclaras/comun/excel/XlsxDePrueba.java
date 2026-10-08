package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Archivos .xlsx generados DENTRO de las pruebas (nada se descarga): libros armados con POI, libros "crudos" escritos
 * a mano parte por parte y versiones maliciosas (zip bomb, macros, vínculos externos, DOCTYPE).
 */
public final class XlsxDePrueba {

	/** Valor de celda que se escribe como fórmula. */
	public record Formula(String expresion) {
	}

	/** Valor de celda que se escribe como error de Excel (#N/A). */
	public record ErrorExcel() {
	}

	private XlsxDePrueba() {
	}

	/**
	 * Llena la hoja {@code hoja} de un libro existente (por ejemplo, la plantilla oficial) desde la fila indicada
	 * (0 = fila 1). Cada valor: {@code String} (texto), {@code Number} (número), {@link Formula}, {@link ErrorExcel}
	 * o {@code null} (vacía).
	 */
	public static byte[] llenar(byte[] libroBase, String hoja, int primeraFila, List<List<Object>> filas) {
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(libroBase));
				ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			XSSFSheet datos = libro.getSheet(hoja);
			for (int i = 0; i < filas.size(); i++) {
				XSSFRow fila = datos.getRow(primeraFila + i) == null ? datos.createRow(primeraFila + i)
						: datos.getRow(primeraFila + i);
				List<Object> valores = filas.get(i);
				for (int c = 0; c < valores.size(); c++) {
					escribir(fila.createCell(c), valores.get(c));
				}
			}
			libro.write(salida);
			return salida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Libro nuevo con una sola hoja y las filas indicadas desde la fila 1. */
	public static byte[] libro(String hoja, List<List<Object>> filas) {
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			libro.createSheet(hoja);
			libro.write(salida);
			return llenar(salida.toByteArray(), hoja, 0, filas);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void escribir(XSSFCell celda, Object valor) {
		if (valor == null) {
			return;
		}
		if (valor instanceof Number numero) {
			celda.setCellValue(numero.doubleValue());
		}
		else if (valor instanceof Formula formula) {
			celda.setCellFormula(formula.expresion());
		}
		else if (valor instanceof ErrorExcel) {
			celda.setCellErrorValue(FormulaError.NA);
		}
		else if (valor instanceof Boolean booleano) {
			celda.setCellValue(booleano);
		}
		else {
			celda.setCellValue(valor.toString());
		}
	}

	/**
	 * Libro mínimo escrito a mano: una hoja «Alumnos» con el {@code <sheetData>} indicado (XML) y, si se pide, el
	 * sistema de fechas 1904. Sirve para probar celdas que Excel escribe y POI no (texto en línea, guía fonética…).
	 */
	public static byte[] crudo(String sheetData, boolean fecha1904, String sharedStrings, String antesDeLaHoja) {
		Map<String, byte[]> partes = new LinkedHashMap<>();
		partes.put("[Content_Types].xml", ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
				+ "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
				+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
				+ "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
				+ "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
				+ (sharedStrings == null ? "" : "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>")
				+ "</Types>").getBytes(StandardCharsets.UTF_8));
		partes.put("_rels/.rels", ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
				+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
				+ "</Relationships>").getBytes(StandardCharsets.UTF_8));
		partes.put("xl/workbook.xml", ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
				+ "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
				+ (fecha1904 ? "<workbookPr date1904=\"1\"/>" : "<workbookPr/>")
				+ "<sheets><sheet name=\"Alumnos\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
				.getBytes(StandardCharsets.UTF_8));
		partes.put("xl/_rels/workbook.xml.rels", ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
				+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
				+ (sharedStrings == null ? "" : "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>")
				+ "</Relationships>").getBytes(StandardCharsets.UTF_8));
		partes.put("xl/worksheets/sheet1.xml", ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ (antesDeLaHoja == null ? "" : antesDeLaHoja)
				+ "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
				+ sheetData + "</sheetData></worksheet>").getBytes(StandardCharsets.UTF_8));
		if (sharedStrings != null) {
			partes.put("xl/sharedStrings.xml", ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
					+ "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" + sharedStrings
					+ "</sst>").getBytes(StandardCharsets.UTF_8));
		}
		return zip(partes);
	}

	public static byte[] crudo(String sheetData) {
		return crudo(sheetData, false, null, null);
	}

	/** Copia el libro agregando una parte (por ejemplo, {@code xl/vbaProject.bin}). */
	public static byte[] conParte(byte[] libro, String nombre, byte[] contenido) {
		Map<String, byte[]> partes = partes(libro);
		partes.put(nombre, contenido);
		return zip(partes);
	}

	/** Copia el libro cambiando el texto de una parte. */
	public static byte[] cambiarParte(byte[] libro, String nombre, UnaryOperator<String> cambio) {
		Map<String, byte[]> partes = partes(libro);
		partes.put(nombre, cambio.apply(new String(partes.get(nombre), StandardCharsets.UTF_8))
				.getBytes(StandardCharsets.UTF_8));
		return zip(partes);
	}

	/** Zip bomb: la hoja 1 con {@code megas} MB de espacios (comprime a unos pocos KB). */
	public static byte[] zipBomb(byte[] libro, int megas) {
		Map<String, byte[]> partes = partes(libro);
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(salida)) {
			for (Map.Entry<String, byte[]> parte : partes.entrySet()) {
				zip.putNextEntry(new ZipEntry(parte.getKey()));
				if (parte.getKey().equals("xl/worksheets/sheet1.xml")) {
					String xml = new String(parte.getValue(), StandardCharsets.UTF_8);
					int corte = xml.indexOf("<sheetData");
					zip.write(xml.substring(0, corte).getBytes(StandardCharsets.UTF_8));
					byte[] espacios = new byte[1 << 20];
					Arrays.fill(espacios, (byte) ' ');
					for (int i = 0; i < megas; i++) {
						zip.write(espacios);
					}
					zip.write(xml.substring(corte).getBytes(StandardCharsets.UTF_8));
				}
				else {
					zip.write(parte.getValue());
				}
				zip.closeEntry();
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return salida.toByteArray();
	}

	/** Un zip con {@code cantidad} entradas pequeñas (más el [Content_Types].xml). */
	public static byte[] zipConEntradas(int cantidad) {
		Map<String, byte[]> partes = new LinkedHashMap<>();
		partes.put("[Content_Types].xml", "<Types/>".getBytes(StandardCharsets.UTF_8));
		for (int i = 0; i < cantidad; i++) {
			partes.put("xl/relleno/parte" + i + ".xml", "<a/>".getBytes(StandardCharsets.UTF_8));
		}
		return zip(partes);
	}

	public static Map<String, byte[]> partes(byte[] libro) {
		Map<String, byte[]> partes = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(libro))) {
			ZipEntry entrada;
			while ((entrada = zip.getNextEntry()) != null) {
				partes.put(entrada.getName(), zip.readAllBytes());
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return partes;
	}

	private static byte[] zip(Map<String, byte[]> partes) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(salida)) {
			for (Map.Entry<String, byte[]> parte : partes.entrySet()) {
				zip.putNextEntry(new ZipEntry(parte.getKey()));
				zip.write(parte.getValue());
				zip.closeEntry();
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return salida.toByteArray();
	}
}
