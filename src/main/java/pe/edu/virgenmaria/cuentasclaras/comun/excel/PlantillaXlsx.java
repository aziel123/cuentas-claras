package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Plantilla descargable: hojas «Instrucciones», la de datos (vacía, solo encabezados) y «Listas» (oculta, con los
 * valores de las listas desplegables).
 * <ul>
 *   <li>Las columnas de documentos y teléfonos tienen formato Texto (@): Excel no les quita el cero inicial.</li>
 *   <li>Listas desplegables en los campos con valores fijos y fila 1 inmovilizada.</li>
 *   <li>Sin fila de ejemplo en la hoja de datos: el ejemplo va en «Instrucciones» (no se importa por error).</li>
 * </ul>
 */
@Component
public class PlantillaXlsx {

	/** Filas con validación de lista: el máximo de filas de datos más holgura. */
	private static final int FILAS_CON_VALIDACION = 2500;

	public byte[] crear(String hojaDatos, List<ColumnaPlantilla> columnas, List<String> instrucciones,
			List<String> filaEjemplo) {
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			CellStyle titulo = estiloNegrita(libro, false);
			CellStyle encabezado = estiloNegrita(libro, true);
			DataFormat formatos = libro.createDataFormat();
			CellStyle texto = libro.createCellStyle();
			texto.setDataFormat(formatos.getFormat("@"));

			XSSFSheet hojaInstrucciones = libro.createSheet("Instrucciones");
			int fila = 0;
			Row primera = hojaInstrucciones.createRow(fila++);
			primera.createCell(0).setCellValue("Cómo llenar la hoja «" + hojaDatos + "»");
			primera.getCell(0).setCellStyle(titulo);
			for (String linea : instrucciones) {
				hojaInstrucciones.createRow(fila++).createCell(0).setCellValue(linea);
			}
			fila++;
			Row cabeceraAyuda = hojaInstrucciones.createRow(fila++);
			cabeceraAyuda.createCell(0).setCellValue("Columna");
			cabeceraAyuda.createCell(1).setCellValue("Qué escribir");
			cabeceraAyuda.createCell(2).setCellValue("Ejemplo (ficticio)");
			cabeceraAyuda.getCell(0).setCellStyle(encabezado);
			cabeceraAyuda.getCell(1).setCellStyle(encabezado);
			cabeceraAyuda.getCell(2).setCellStyle(encabezado);
			for (int c = 0; c < columnas.size(); c++) {
				ColumnaPlantilla columna = columnas.get(c);
				Row ayuda = hojaInstrucciones.createRow(fila++);
				ayuda.createCell(0).setCellValue(Columnas.letra(c) + " · " + columna.encabezado());
				ayuda.createCell(1).setCellValue(columna.ayuda() == null ? "" : columna.ayuda());
				ayuda.createCell(2).setCellValue(c < filaEjemplo.size() ? filaEjemplo.get(c) : "");
			}
			hojaInstrucciones.setColumnWidth(0, 45 * 256);
			hojaInstrucciones.setColumnWidth(1, 90 * 256);
			hojaInstrucciones.setColumnWidth(2, 30 * 256);

			XSSFSheet datos = libro.createSheet(hojaDatos);
			XSSFSheet listas = libro.createSheet("Listas");
			Row cabecera = datos.createRow(0);
			DataValidationHelper ayudante = datos.getDataValidationHelper();
			int columnaLista = 0;
			for (int c = 0; c < columnas.size(); c++) {
				ColumnaPlantilla columna = columnas.get(c);
				cabecera.createCell(c).setCellValue(columna.encabezado());
				cabecera.getCell(c).setCellStyle(encabezado);
				datos.setColumnWidth(c, Math.max(columna.ancho(), 8) * 256);
				if (columna.texto()) {
					datos.setDefaultColumnStyle(c, texto);
				}
				if (!columna.lista().isEmpty()) {
					for (int i = 0; i < columna.lista().size(); i++) {
						Row filaLista = listas.getRow(i) == null ? listas.createRow(i) : listas.getRow(i);
						filaLista.createCell(columnaLista).setCellValue(columna.lista().get(i));
					}
					String letra = Columnas.letra(columnaLista);
					DataValidationConstraint restriccion = ayudante.createFormulaListConstraint(
							"Listas!$" + letra + "$1:$" + letra + "$" + columna.lista().size());
					DataValidation validacion = ayudante.createValidation(restriccion,
							new CellRangeAddressList(1, FILAS_CON_VALIDACION, c, c));
					validacion.setShowErrorBox(true);
					validacion.createErrorBox("Valor no válido", "Elige un valor de la lista.");
					if (columna.ayuda() != null) {
						validacion.setShowPromptBox(true);
						validacion.createPromptBox(columna.encabezado(), recortar(columna.ayuda(), 250));
					}
					datos.addValidationData(validacion);
					columnaLista++;
				}
			}
			datos.createFreezePane(0, 1);
			libro.setSheetHidden(libro.getSheetIndex(listas), true);
			libro.setActiveSheet(libro.getSheetIndex(datos));
			libro.setSelectedTab(libro.getSheetIndex(datos));
			libro.write(salida);
			return salida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar la plantilla", e);
		}
	}

	private static CellStyle estiloNegrita(XSSFWorkbook libro, boolean conFondo) {
		Font negrita = libro.createFont();
		negrita.setBold(true);
		CellStyle estilo = libro.createCellStyle();
		estilo.setFont(negrita);
		if (conFondo) {
			estilo.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
			estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		}
		return estilo;
	}

	private static String recortar(String texto, int maximo) {
		return texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}
}
