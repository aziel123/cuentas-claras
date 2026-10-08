package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Único escritor de reportes en Excel (sprint 6, decisión 9). Garantías:
 * <ul>
 *   <li>nunca escribe una fórmula (ArchUnit: nadie llama a {@code setCellFormula}) ni un hipervínculo, comentario,
 *       macro u hoja oculta: el archivo es {@code .xlsx} con valores;</li>
 *   <li>todo texto pasa por {@link TextoCelda} (sin caracteres de control; con comilla si parece fórmula);</li>
 *   <li>los montos van como número con formato {@code #,##0.00}, convertidos solo en {@link CeldaDinero}.</li>
 * </ul>
 * Qué columnas lleva cada reporte (datos mínimos, Ley 29733) lo decide quien lo arma, no este escritor.
 */
@Component
public class EscritorXlsxSeguro {

	/** Tope de filas por hoja (configuración del sprint 6: exportacion-max-filas). */
	public static final int MAX_FILAS = 20_000;

	public byte[] escribir(List<HojaReporte> hojas) {
		if (hojas == null || hojas.isEmpty()) {
			throw new IllegalArgumentException("Un reporte tiene al menos una hoja");
		}
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			DataFormat formatos = libro.createDataFormat();
			Font negrita = libro.createFont();
			negrita.setBold(true);
			CellStyle encabezado = libro.createCellStyle();
			encabezado.setFont(negrita);
			encabezado.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
			encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			CellStyle texto = libro.createCellStyle();
			texto.setDataFormat(formatos.getFormat("@"));
			CellStyle textoConComilla = libro.createCellStyle();
			textoConComilla.setDataFormat(formatos.getFormat("@"));
			textoConComilla.setQuotePrefixed(true);
			CellStyle dinero = libro.createCellStyle();
			dinero.setDataFormat(formatos.getFormat("#,##0.00"));
			CellStyle fecha = libro.createCellStyle();
			fecha.setDataFormat(formatos.getFormat("dd/mm/yyyy"));
			CellStyle entero = libro.createCellStyle();
			entero.setDataFormat(formatos.getFormat("0"));
			Estilos estilos = new Estilos(texto, textoConComilla, dinero, fecha, entero);

			for (HojaReporte hoja : hojas) {
				if (hoja.filas().size() > MAX_FILAS) {
					throw new IllegalArgumentException("La hoja " + hoja.nombre() + " supera el máximo de filas");
				}
				XSSFSheet sheet = libro.createSheet(hoja.nombre());
				Row cabecera = sheet.createRow(0);
				for (int c = 0; c < hoja.encabezados().size(); c++) {
					Cell celda = cabecera.createCell(c);
					TextoCelda.Resultado titulo = TextoCelda.preparar(hoja.encabezados().get(c));
					celda.setCellValue(titulo.texto());
					celda.setCellStyle(encabezado);
					sheet.setColumnWidth(c, Math.min(Math.max(titulo.texto().length() + 4, 14), 60) * 256);
				}
				int numero = 1;
				for (List<Celda> fila : hoja.filas()) {
					Row row = sheet.createRow(numero++);
					for (int c = 0; c < fila.size(); c++) {
						escribir(row.createCell(c), fila.get(c), estilos);
					}
				}
				if (!hoja.encabezados().isEmpty()) {
					sheet.createFreezePane(0, 1);
				}
			}
			libro.setActiveSheet(0);
			libro.write(salida);
			return salida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar el reporte", e);
		}
	}

	private record Estilos(CellStyle texto, CellStyle textoConComilla, CellStyle dinero, CellStyle fecha,
			CellStyle entero) {
	}

	private static void escribir(Cell celda, Celda valor, Estilos estilos) {
		switch (valor) {
			case Celda.Texto t -> escribirTexto(celda, t.valor(), estilos);
			case Celda.Dinero d -> {
				CeldaDinero.escribir(celda, d.valor());
				celda.setCellStyle(estilos.dinero());
			}
			case Celda.Fecha f -> {
				if (f.valor() == null) {
					escribirTexto(celda, "", estilos);
				}
				else {
					celda.setCellValue(f.valor());
					celda.setCellStyle(estilos.fecha());
				}
			}
			case Celda.Entero e -> {
				CeldaDinero.escribirEntero(celda, e.valor());
				celda.setCellStyle(estilos.entero());
			}
		}
	}

	private static void escribirTexto(Cell celda, String valor, Estilos estilos) {
		TextoCelda.Resultado texto = TextoCelda.preparar(valor);
		celda.setCellValue(texto.texto());
		celda.setCellStyle(texto.conComilla() ? estilos.textoConComilla() : estilos.texto());
	}
}
