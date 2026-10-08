package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.LectorXlsxSeguro;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.PropiedadesExcel;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ValidadorArchivoXlsx;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.XlsxDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sprint 4, tanda 3: el formato genérico del extracto (sección 10.3 del diseño), en CSV y en XLSX. Puro. */
class FormatoExtractoTest {

	private static final String CABECERA = "cuenta;191-2345678-0-12\nfecha;descripcion;numero_operacion;referencia;cargo;abono;saldo\n";

	private final FormatoExtractoCsv csv = new FormatoExtractoCsv(PropiedadesConciliacion.porDefecto());

	private LecturaExtracto leer(String contenido) {
		return csv.leer("extracto.csv", contenido.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void leeLaCuentaYLosMovimientosConSuSaldo() {
		LecturaExtracto lectura = leer(CABECERA + "2026-10-02;ABONO YAPE;YP-0012 34;;;450.00;1450.00\n"
				+ "02/10/2026;COMISION;;REF 9;0.00;12.50;1462.50\n" + "2026-10-03;ITF;;;0.45;;1462.05\n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.cuenta()).isEqualTo("191-2345678-0-12");
		assertThat(lectura.formato()).isEqualTo("GENERICO_CSV");
		assertThat(lectura.filas()).hasSize(3);
		FilaExtracto yape = lectura.filas().getFirst();
		assertThat(yape.tipo()).isEqualTo(TipoMovimiento.ABONO);
		assertThat(yape.operacion()).isEqualTo("YP001234");
		assertThat(yape.monto()).isEqualTo(new BigDecimal("450.00"));
		assertThat(yape.saldo()).isEqualTo(new BigDecimal("1450.00"));
		// Un 0.00 en la otra columna no la convierte en cargo y abono a la vez.
		assertThat(lectura.filas().get(1).tipo()).isEqualTo(TipoMovimiento.ABONO);
		assertThat(lectura.filas().get(1).referencia()).isEqualTo("REF 9");
		assertThat(lectura.filas().get(2).tipo()).isEqualTo(TipoMovimiento.CARGO);
		assertThat(lectura.filas().get(2).linea()).isEqualTo(5);
	}

	/** F14: el saldo corre fila por fila; un abono agregado a mano sin rehacer los saldos no cuadra. */
	@Test
	void saldoCorridoInconsistenteSeRechaza() {
		LecturaExtracto lectura = leer(CABECERA + "2026-10-02;ABONO YAPE;YP1;;;450.00;1450.00\n"
				+ "2026-10-02;ABONO AGREGADO;YP2;;;450.00;1450.00\n");

		assertThat(lectura.errores()).singleElement().satisfies(e -> {
			assertThat(e.linea()).isEqualTo(4);
			assertThat(e.mensaje()).contains("el saldo no cuadra").contains("incompleto o alterado");
		});
	}

	@Test
	void sinSaldoElArchivoNoSirve() {
		assertThatThrownBy(() -> leer("cuenta;191\nfecha;descripcion;cargo;abono\n2026-10-02;X;;1.00\n"))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("cabecera");
		assertThatThrownBy(() -> leer("cuenta;191\nfecha;descripcion;cargo;saldo\n2026-10-02;X;;1.00\n"))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("abono");
		assertThat(leer(CABECERA + "2026-10-02;ABONO;;;;450.00;\n").errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("el saldo es obligatorio"));
	}

	@Test
	void sinCuentaNoSeSabeDeQuienEs() {
		assertThatThrownBy(() -> leer("fecha;descripcion;cargo;abono;saldo\n2026-10-02;X;;1.00;1.00\n"))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("de qué cuenta");
	}

	/** La cuenta también puede venir como columna; una fila de otra cuenta es un error. */
	@Test
	void cuentaComoColumna() {
		LecturaExtracto lectura = leer("cuenta;fecha;descripcion;cargo;abono;saldo\n"
				+ "19123456780 12;2026-10-02;A;;1.00;101.00\n191-2345678-0-99;2026-10-02;B;;1.00;102.00\n");

		assertThat(lectura.cuenta()).isEqualTo("19123456780 12");
		assertThat(lectura.errores()).singleElement().satisfies(e -> assertThat(e.mensaje()).contains("otra cuenta"));
	}

	@Test
	void cargoYAbonoALaVezOMontoConComaSonErrores() {
		LecturaExtracto lectura = leer(CABECERA + "2026-10-02;A;;;1.00;2.00;101.00\n2026-10-02;B;;;;1,250.00;1351.00\n"
				+ "2026-31-02;C;;;;1.00;1352.00\n");

		assertThat(lectura.errores()).extracting(ErrorExtracto::mensaje).satisfiesExactly(
				m -> assertThat(m).contains("cargo O un abono"), m -> assertThat(m).contains("punto decimal"),
				m -> assertThat(m).contains("la fecha"));
	}

	@Test
	void filasFueraDeOrdenSonError() {
		assertThat(leer(CABECERA + "2026-10-03;A;;;;1.00;101.00\n2026-10-02;B;;;;1.00;102.00\n").errores())
				.singleElement().satisfies(e -> assertThat(e.mensaje()).contains("orden del banco"));
	}

	/** La glosa del banco no puede llegar a un Excel como fórmula; la operación inválida no se usa para emparejar. */
	@Test
	void glosaConFormulaSeNeutralizaYOperacionInvalidaSeIgnora() {
		LecturaExtracto lectura = leer(CABECERA + "2026-10-02;=HYPERLINK(\"x\");00012;;;1.00;101.00\n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.filas().getFirst().descripcion()).startsWith("'=");
		assertThat(lectura.filas().getFirst().operacion()).isNull();
	}

	@Test
	void xlsxConFechaDeExcelYNumeros() {
		PropiedadesExcel excel = PropiedadesExcel.porDefecto();
		FormatoExtractoXlsx xlsx = new FormatoExtractoXlsx(new ValidadorArchivoXlsx(excel), new LectorXlsxSeguro(excel),
				PropiedadesConciliacion.porDefecto());
		byte[] libro = XlsxDePrueba.libro("Movimientos", List.of(
				List.of("cuenta", "191-2345678-0-12"),
				List.of("fecha", "descripcion", "numero_operacion", "referencia", "cargo", "abono", "saldo"),
				List.of(46297, "ABONO YAPE", 123456, "", "", 450, 1450),
				List.of("2026-10-02", "ITF", "", "", "0.45", "", "1449.55")));

		LecturaExtracto lectura = xlsx.leer("extracto.xlsx", libro);

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.formato()).isEqualTo("GENERICO_XLSX");
		assertThat(lectura.cuenta()).isEqualTo("191-2345678-0-12");
		assertThat(lectura.filas().getFirst().fecha()).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(lectura.filas().getFirst().operacion()).isEqualTo("123456");
		assertThat(lectura.filas().get(1).saldo()).isEqualByComparingTo("1449.55");
		assertThat(xlsx.acepta("EXTRACTO.XLSX")).isTrue();
		assertThat(csv.acepta("extracto.xlsx")).isFalse();
	}
}
