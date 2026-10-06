package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.LectorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.PropiedadesExcel;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValidadorArchivoXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.XlsxDePrueba;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El formato genérico de recaudación (sección 10.3 del diseño), en CSV y en XLSX. Puro. */
class FormatoGenericoTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 6);

	private static final String CABECERA = "fecha_pago;codigo_alumno;referencia_deuda;monto;moneda;numero_operacion;canal\n";

	private final FormatoGenericoCsv csv = new FormatoGenericoCsv(PropiedadesRecaudacion.porDefecto());

	private LecturaRecaudacion leer(String contenido) {
		return csv.leer("banco.csv", contenido.getBytes(StandardCharsets.UTF_8), HOY);
	}

	@Test
	void leeLasLineasYElPie() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;000000018;450.00;PEN;123456;Agente\n"
				+ "05/10/2026;0000 0026;;350;pen;789012;\n" + "TOTAL;800.00;2\n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.formato()).isEqualTo("GENERICO_CSV");
		assertThat(lectura.totalDeclarado()).isEqualByComparingTo("800.00");
		assertThat(lectura.cantidadDeclarada()).isEqualTo(2);
		assertThat(lectura.fechaProceso()).isEqualTo(LocalDate.of(2026, 10, 5));
		assertThat(lectura.filas()).extracting(FilaRecaudacion::numero).containsExactly(1, 2);
		FilaRecaudacion segunda = lectura.filas().get(1);
		assertThat(segunda.codigo()).isEqualTo("00000026");
		assertThat(segunda.referenciaDeuda()).isNull();
		assertThat(segunda.monto()).isEqualTo(new BigDecimal("350.00"));
		assertThat(segunda.moneda()).isEqualTo("PEN");
		assertThat(segunda.linea()).isEqualTo(3);
	}

	@Test
	void pieDistintoDeLaSumaEsError() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;123456;\nTOTAL;450.10;1\n");
		assertThat(lectura.errores()).singleElement().satisfies(e -> {
			assertThat(e.linea()).isEqualTo(3);
			assertThat(e.mensaje()).contains("no es la suma");
		});
		assertThat(leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;123456;\nTOTAL;450.00;2\n").errores())
				.singleElement().satisfies(e -> assertThat(e.mensaje()).contains("dice 2 pagos"));
	}

	@Test
	void montoConComaEsError() {
		for (String monto : List.of("450,00", "1,250.00", "S/ 450", "450.001", "-450.00", "0.00", "100000.00")) {
			LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;" + monto + ";PEN;123456;\n");
			assertThat(lectura.errores()).as(monto).singleElement()
					.satisfies(e -> assertThat(e.mensaje()).contains("monto"));
			assertThat(lectura.filas()).as(monto).isEmpty();
		}
	}

	@Test
	void fechaFueraDeFormatoEsError() {
		for (String fecha : List.of("2026/10/05", "5/10/2026", "31/02/2026", "ayer", "2026-10-07")) {
			assertThat(leer(CABECERA + fecha + ";00000018;;450.00;PEN;123456;\n").errores()).as(fecha).singleElement()
					.satisfies(e -> assertThat(e.mensaje()).contains("fecha"));
		}
	}

	@Test
	void operacionSeGuardaCanonica() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;0012-3456 ab;\n");

		assertThat(lectura.filas().getFirst().operacion()).isEqualTo("123456AB");
		assertThat(leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;12;\n").errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("operación"));
	}

	@Test
	void monedaDistintaDePenOUsdEsError() {
		assertThat(leer(CABECERA + "2026-10-05;00000018;;450.00;USD;123456;\n").filas().getFirst().moneda())
				.isEqualTo("USD");
		assertThat(leer(CABECERA + "2026-10-05;00000018;;450.00;EUR;123456;\n").errores()).hasSize(1);
	}

	@Test
	void sinLasColumnasObligatoriasNoSeLee() {
		assertThatThrownBy(() -> leer("fecha;codigo;monto\n2026-10-05;00000018;450.00\n"))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("Faltan columnas");
		assertThatThrownBy(() -> leer(CABECERA)).isInstanceOf(ArchivoNoValidoException.class)
				.hasMessageContaining("no tiene pagos");
	}

	/** El mismo formato en Excel: un código escrito como número recupera sus ceros y una fecha de Excel se convierte. */
	@Test
	void xlsxConCodigoNumericoYFechaDeExcel() {
		PropiedadesExcel excel = PropiedadesExcel.porDefecto();
		FormatoGenericoXlsx xlsx = new FormatoGenericoXlsx(new ValidadorArchivoXlsx(excel), new LectorXlsxSeguro(excel),
				PropiedadesRecaudacion.porDefecto());
		byte[] libro = XlsxDePrueba.libro("Pagos", List.of(
				List.of("fecha_pago", "codigo_alumno", "referencia_deuda", "monto", "moneda", "numero_operacion"),
				List.of(46298, 18, 18, 450, "PEN", 123456),
				List.of("2026-10-05", "00000026", "", "350.50", "PEN", "AB-77"),
				List.of("TOTAL", "800.50", 2)));

		LecturaRecaudacion lectura = xlsx.leer("banco.xlsx", libro, HOY);

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.formato()).isEqualTo("GENERICO_XLSX");
		FilaRecaudacion primera = lectura.filas().getFirst();
		assertThat(primera.codigo()).isEqualTo("00000018");
		assertThat(primera.referenciaDeuda()).isEqualTo("000000018");
		assertThat(primera.fechaPago()).isEqualTo(LocalDate.of(2026, 10, 3));
		assertThat(primera.monto()).isEqualTo(new BigDecimal("450.00"));
		assertThat(primera.operacion()).isEqualTo("123456");
		assertThat(lectura.filas().get(1).operacion()).isEqualTo("AB77");
		assertThat(xlsx.acepta("BANCO.XLSX")).isTrue();
		assertThat(csv.acepta("banco.xlsx")).isFalse();
	}
}
