package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.config.PropiedadesConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 4: extractos con bordes de dinero y de formato: saldo en sobregiro, céntimos en el saldo corrido, ceros
 * en la columna contraria, montos con coma, CRLF con BOM, preámbulo largo y cuentas mezcladas. Puro.
 */
class FormatoExtractoBordesTest {

	private static final String CABECERA = "cuenta;191-2345678-0-12\nfecha;descripcion;numero_operacion;referencia;cargo;abono;saldo\n";

	private final FormatoExtractoCsv csv = new FormatoExtractoCsv(PropiedadesConciliacion.porDefecto());

	private LecturaExtracto leer(String contenido) {
		return csv.leer("extracto.csv", contenido.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void debeAceptarUnSaldoQueCruzaASobregiro() {
		LecturaExtracto lectura = leer(CABECERA + "2026-10-02;PAGO PROVEEDOR;;;100.50;;-0.50\n"
				+ "2026-10-02;ABONO YAPE;YP1234;;;0.50;0.00\n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.filas().get(0).saldo()).isEqualByComparingTo("-0.50");
		assertThat(lectura.filas().get(1).saldo()).isEqualByComparingTo("0.00");
	}

	@Test
	void debeRechazarUnSaldoCorridoQueDifiereEnUnCentimo() {
		LecturaExtracto lectura = leer(CABECERA + "2026-10-02;ABONO YAPE;YP1234;;;450.00;1450.00\n"
				+ "2026-10-02;ABONO PLIN;PL5678;;;380.00;1830.01\n");

		assertThat(lectura.errores()).singleElement().satisfies(e -> assertThat(e.mensaje()).contains("no cuadra"));
	}

	@Test
	void debeAceptarFilasDelMismoDiaEnElOrdenDelBanco() {
		LecturaExtracto lectura = leer(CABECERA + "2026-10-02;A;;;;10.00;10.00\n" + "2026-10-02;B;;;5.00;;5.00\n"
				+ "2026-10-02;C;;;;0.01;5.01\n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.filas()).extracting(FilaExtracto::tipo).containsExactly(TipoMovimiento.ABONO,
				TipoMovimiento.CARGO, TipoMovimiento.ABONO);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.0", "0.00", "000.00", " " })
	void debeTratarUnCeroEnElCargoComoCeldaVacia(String cero) {
		LecturaExtracto lectura = leer(CABECERA + "2026-10-02;ABONO;;;" + cero + ";450.00;450.00\n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.filas().getFirst().tipo()).isEqualTo(TipoMovimiento.ABONO);
	}

	@Test
	void debeRechazarUnaFilaConCargoYAbonoEnCero() {
		assertThat(leer(CABECERA + "2026-10-02;NADA;;;0.00;0.00;450.00\n").errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("cargo O un abono"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1,450.00", "1450,00", "1450.001", "S/1450.00", "" })
	void debeRechazarUnSaldoMalEscrito(String saldo) {
		assertThat(leer(CABECERA + "2026-10-02;ABONO;;;;450.00;\"" + saldo + "\"\n").errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("saldo"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "450,00", "-450.00", "450.005" })
	void debeRechazarUnAbonoMalEscritoONegativo(String abono) {
		assertThat(leer(CABECERA + "2026-10-02;ABONO;;;;\"" + abono + "\";450.00\n").errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("monto"));
	}

	@Test
	void debeLeerUnExtractoConBomCrlfYComasComoSeparador() {
		byte[] archivo = ("﻿cuenta,191-2345678-0-12\r\nfecha,descripcion,numero_operacion,referencia,cargo,abono,saldo\r\n"
				+ "2026-10-02,\"ABONO YAPE, ROSA\",YP1234,,,450.00,1450.00\r\n").getBytes(StandardCharsets.UTF_8);

		LecturaExtracto lectura = csv.leer("extracto.csv", archivo);

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.cuenta()).isEqualTo("191-2345678-0-12");
		assertThat(lectura.filas().getFirst().descripcion()).isEqualTo("ABONO YAPE, ROSA");
	}

	@Test
	void debeEncontrarLaCabeceraDespuesDeUnPreambuloDeVariasLineas() {
		String preambulo = "Banco de Credito;\nEstado de cuenta;\ncuenta;191-2345678-0-12\nmoneda;PEN\n";

		LecturaExtracto lectura = leer(preambulo
				+ "fecha;descripcion;numero_operacion;referencia;cargo;abono;saldo\n2026-10-02;A;;;;10.00;10.00\n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.cuenta()).isEqualTo("191-2345678-0-12");
	}

	@Test
	void debeNoBuscarLaCabeceraMasAllaDeOnceLineas() {
		String preambulo = "cuenta;191-2345678-0-12\n" + "linea;x\n".repeat(11);

		assertThatThrownBy(() -> leer(preambulo
				+ "fecha;descripcion;numero_operacion;referencia;cargo;abono;saldo\n2026-10-02;A;;;;10.00;10.00\n"))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("cabecera");
	}

	@Test
	void debeRechazarUnaFilaDeOtraCuentaAunqueVengaConOtroFormato() {
		String cabecera = "cuenta;fecha;descripcion;numero_operacion;referencia;cargo;abono;saldo\n";

		LecturaExtracto lectura = leer(cabecera + "191-2345678-0-12;2026-10-02;A;;;;10.00;10.00\n"
				+ "19123456780 13;2026-10-02;B;;;;10.00;20.00\n" + "191 2345678 0 12;2026-10-02;C;;;;10.00;20.00\n");

		assertThat(lectura.errores()).singleElement().satisfies(e -> {
			assertThat(e.linea()).isEqualTo(3);
			assertThat(e.mensaje()).contains("otra cuenta");
		});
	}

	@Test
	void debeGuardarLaOperacionDelBancoEnFormaCanonica() {
		LecturaExtracto lectura = leer(CABECERA + "2026-10-02;ABONO;yp-0012 34;;;450.00;450.00\n"
				+ "2026-10-02;ABONO;12;;;1.00;451.00\n");

		assertThat(lectura.filas().get(0).operacion()).isEqualTo("YP001234");
		// Una operación del banco que no se puede llevar a la forma canónica no rompe la lectura: queda sin operación.
		assertThat(lectura.filas().get(1).operacion()).isNull();
	}

	@Test
	void debeRecortarLaGlosaA200CaracteresSinPerderElApostrofoDeUnaFormula() {
		String glosa = "=HYPERLINK(\"x\")" + "A".repeat(300);

		FilaExtracto fila = leer(CABECERA + "2026-10-02;\"" + glosa.replace("\"", "\"\"") + "\";;;;1.00;1.00\n").filas()
				.getFirst();

		assertThat(fila.descripcion()).hasSize(200).startsWith("'=");
	}
}
