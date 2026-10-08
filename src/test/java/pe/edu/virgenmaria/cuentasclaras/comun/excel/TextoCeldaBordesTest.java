package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA sprint 6: bordes de la defensa contra fórmulas del Excel del contador (sección 10.2), además de los 12 ataques de
 * {@code EscritorXlsxSeguroTest}.
 * <ul>
 *   <li>Dado un texto que empieza con espacios y caracteres de control antes del «=», cuando se escribe, entonces lleva
 *       comilla y apóstrofo y no lleva los controles.</li>
 *   <li>Dado el signo menos de ancho completo, cuando se escribe, entonces lleva comilla.</li>
 *   <li>Dado un texto de más de 500 caracteres con un emoji en el corte, cuando se recorta, entonces no queda medio
 *       carácter.</li>
 *   <li>Dado un texto común («Pensión de marzo 2027», un número de operación), entonces no lleva comilla.</li>
 *   <li>Dado un monto con 2 decimales en el límite del reporte, entonces se lee igual al centavo.</li>
 * </ul>
 */
class TextoCeldaBordesTest {

	private final EscritorXlsxSeguro escritor = new EscritorXlsxSeguro();

	private Cell escrita(Celda celda) throws IOException {
		byte[] archivo = escritor.escribir(List.of(new HojaReporte("Datos", List.of("Valor"), List.of(List.of(celda)))));
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
			return libro.getSheet("Datos").getRow(1).getCell(0);
		}
	}

	@Test
	void debePonerComillaCuandoElIgualVieneDespuesDeEspaciosYControles() throws IOException {
		Cell celda = escrita(Celda.texto("  \u0007 \t=HYPERLINK(\"http://x\")"));
		assertThat(celda.getCellType()).isEqualTo(CellType.STRING);
		assertThat(celda.getCellStyle().getQuotePrefixed()).isTrue();
		assertThat(celda.getStringCellValue()).startsWith("'").doesNotContain("\u0007", " ", "\t");
	}

	@Test
	void debePonerComillaConElSignoMenosDeAnchoCompleto() {
		TextoCelda.Resultado r = TextoCelda.preparar("－2+3");
		assertThat(r.conComilla()).isTrue();
		assertThat(r.texto()).isEqualTo("'－2+3");
	}

	@Test
	void debeRecortarA500CaracteresSinPartirUnEmoji() {
		String texto = "a".repeat(499) + "😀" + "zzz";
		TextoCelda.Resultado r = TextoCelda.preparar(texto);
		assertThat(r.texto().codePointCount(0, r.texto().length())).isEqualTo(500);
		assertThat(r.texto()).endsWith("😀");
		assertThat(r.conComilla()).isFalse();
	}

	@Test
	void noDebePonerComillaAUnConceptoNiAUnNumeroDeOperacion() {
		assertThat(TextoCelda.preparar("Pensión de marzo 2027")).isEqualTo(new TextoCelda.Resultado("Pensión de marzo 2027",
				false));
		assertThat(TextoCelda.preparar("YP550011")).isEqualTo(new TextoCelda.Resultado("YP550011", false));
		assertThat(TextoCelda.preparar(null)).isEqualTo(new TextoCelda.Resultado("", false));
	}

	@Test
	void debeLeerseAlCentavoUnMontoEnElLimiteDelReporte() throws IOException {
		Cell celda = escrita(Celda.dinero(new BigDecimal("999999999.99")));
		assertThat(BigDecimal.valueOf(celda.getNumericCellValue())).isEqualByComparingTo("999999999.99");
		Cell centimo = escrita(Celda.dinero(new BigDecimal("0.01")));
		assertThat(BigDecimal.valueOf(centimo.getNumericCellValue())).isEqualByComparingTo("0.01");
	}
}
