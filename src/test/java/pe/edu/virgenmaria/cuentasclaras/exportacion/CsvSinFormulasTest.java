package pe.edu.virgenmaria.cuentasclaras.exportacion;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.EscritorCsv;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (3.9, OWASP «CSV Injection»): el CSV de la exportación es UTF-8 con BOM, coma y CRLF; una celda de
 * texto que empieza con {@code =}, {@code +}, {@code -}, {@code @}, tabulador o retorno lleva un apóstrofo delante (Excel
 * no la ejecuta). Los números (también los negativos) y las fechas no se tocan.
 */
class CsvSinFormulasTest {

	@Test
	void lasCeldasQueSeriaFormulaLlevanApostrofo() {
		for (String peligrosa : List.of("=HYPERLINK(\"http://x\")", "+51 987", "-2+3", "@SUM(A1)", "\tcmd", "\rcmd")) {
			assertThat(EscritorCsv.celda(peligrosa)).as(peligrosa).contains("'" + peligrosa.charAt(0));
		}
		assertThat(EscritorCsv.celda("Quispe, Rosa")).isEqualTo("\"Quispe, Rosa\"");
		assertThat(EscritorCsv.celda("dijo \"hola\"")).isEqualTo("\"dijo \"\"hola\"\"\"");
		assertThat(EscritorCsv.celda(new BigDecimal("-12.50"))).isEqualTo("-12.50");
		assertThat(EscritorCsv.celda(new BigDecimal("1250.00"))).isEqualTo("1250.00");
		assertThat(EscritorCsv.celda(LocalDate.of(2027, 3, 1))).isEqualTo("2027-03-01");
		assertThat(EscritorCsv.celda(LocalDateTime.of(2027, 3, 1, 8, 30))).isEqualTo("2027-03-01T08:30:00");
		assertThat(EscritorCsv.celda(null)).isEmpty();
		assertThat(EscritorCsv.celda("Ñandú")).isEqualTo("Ñandú");
	}

	@Test
	void elArchivoEsUtf8ConBomYCrlf() {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (EscritorCsv csv = new EscritorCsv(salida)) {
			csv.encabezado(List.of("id", "nombre"));
			csv.fila(Arrays.asList(1L, "=1+1"));
			csv.fila(Arrays.asList(2L, null));
			assertThat(csv.filas()).isEqualTo(2);
		}
		byte[] bytes = salida.toByteArray();
		assertThat(Arrays.copyOf(bytes, 3)).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
		assertThat(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8))
				.isEqualTo("id,nombre\r\n1,'=1+1\r\n2,\r\n");
	}
}
