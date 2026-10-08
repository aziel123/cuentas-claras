package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.config.PropiedadesRecaudacion;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 4: archivos de recaudación malformados o «raros» que un banco real o una persona pueden producir: BOM,
 * CRLF, coma como separador, comillas, celdas vacías, montos con coma o tres decimales, filas extra después del pie,
 * fechas imposibles o futuras y límites de monto. Puro.
 */
class FormatoGenericoBordesTest {

	/** Martes 06/10/2026. */
	private static final LocalDate HOY = LocalDate.of(2026, 10, 6);

	private static final String CABECERA = "fecha_pago;codigo_alumno;referencia_deuda;monto;moneda;numero_operacion;canal\n";

	private final FormatoGenericoCsv csv = new FormatoGenericoCsv(PropiedadesRecaudacion.porDefecto());

	private LecturaRecaudacion leer(String contenido) {
		return csv.leer("banco.csv", contenido.getBytes(StandardCharsets.UTF_8), HOY);
	}

	private LecturaRecaudacion unaLinea(String linea) {
		return leer(CABECERA + linea + "\n");
	}

	@Test
	void debeLeerUnArchivoConBomCrlfComasYComillas() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bytes.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
		bytes.write(("fecha_pago,codigo_alumno,referencia_deuda,monto,moneda,numero_operacion,canal\r\n"
				+ "2026-10-05,\"00000018\",,\"450.00\",PEN,\"OP-123 456\",\"Agente, Lima\"\r\n"
				+ "TOTAL,450.00,1\r\n").getBytes(StandardCharsets.UTF_8));

		LecturaRecaudacion lectura = csv.leer("banco.csv", bytes.toByteArray(), HOY);

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.filas()).singleElement().satisfies(f -> {
			assertThat(f.monto()).isEqualByComparingTo("450.00");
			assertThat(f.codigo()).isEqualTo("00000018");
			assertThat(f.referenciaDeuda()).isNull();
		});
	}

	@Test
	void debeDetectarUnaColumnaRepetidaAunqueUnaVengaConTildes() {
		// «Número_Operación» se normaliza a numero_operacion: queda dos veces en la cabecera.
		assertThatThrownBy(() -> leer("Moneda;Monto;Código_Alumno;Fecha Pago;Número_Operación;numero_operacion\n"
				+ "PEN;450.00;00000018;2026-10-05;OPE001;OPE001\n")).isInstanceOf(ArchivoNoValidoException.class)
				.hasMessageContaining("dos veces");
	}

	@Test
	void debeAceptarColumnasEnCualquierOrdenYColumnasDesconocidas() {
		LecturaRecaudacion lectura = leer("Moneda;Monto;Código_Alumno;Fecha Pago;Numero Operacion;agencia\n"
				+ "pen;450.00;00000018;05/10/2026;OPE001;San Isidro\n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.filas()).singleElement().satisfies(f -> {
			assertThat(f.moneda()).isEqualTo("PEN");
			assertThat(f.fechaPago()).isEqualTo(LocalDate.of(2026, 10, 5));
		});
	}

	@ParameterizedTest
	@ValueSource(strings = { "450,00", "1,250.00", "450.005", "S/ 450.00", "-450.00", "+450.00", "450.", ".50", "",
			"0.00", "100000.00", "4.5e2" })
	void debeRechazarMontosMalEscritosOFueraDeRango(String monto) {
		LecturaRecaudacion lectura = unaLinea("2026-10-05;00000018;;\"" + monto + "\";PEN;OPE001;");

		assertThat(lectura.filas()).isEmpty();
		assertThat(lectura.errores()).singleElement().satisfies(e -> assertThat(e.mensaje()).contains("monto"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.01", "99999.99", "450", "450.5", "0450.00" })
	void debeAceptarMontosEnLosLimites(String monto) {
		LecturaRecaudacion lectura = unaLinea("2026-10-05;00000018;;" + monto + ";PEN;OPE001;");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.filas().getFirst().monto().scale()).isEqualTo(2);
	}

	@Test
	void debeAceptarComoFechaDePagoElDiaDeHoy() {
		assertThat(unaLinea("2026-10-06;00000018;;450.00;PEN;OPE001;").errores()).isEmpty();
	}

	@Test
	void debeRechazarUnaFechaDePagoDeManana() {
		assertThat(unaLinea("07/10/2026;00000018;;450.00;PEN;OPE001;").errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("posterior a hoy"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-02-29", "31/09/2026", "2026-9-30", "30-09-2026", "2026/09/30", "30/09/26" })
	void debeRechazarFechasImposiblesOEnOtroFormato(String fecha) {
		assertThat(unaLinea(fecha + ";00000018;;450.00;PEN;OPE001;").errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("fecha de pago"));
	}

	@Test
	void debeRechazarUnaFilaConCeldasFaltantesSinRomperLasDemas() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;OPE001;\n" + "2026-10-05;00000026\n");

		assertThat(lectura.filas()).hasSize(1);
		assertThat(lectura.errores()).singleElement().satisfies(e -> {
			assertThat(e.linea()).isEqualTo(3);
			assertThat(e.mensaje()).contains("monto").contains("moneda").contains("número de operación");
		});
	}

	@Test
	void debeIgnorarFilasVaciasYDeSoloSeparadoresAlFinal() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;OPE001;\n;;;;;;\n\n   \n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.filas()).hasSize(1);
	}

	@Test
	void debeRechazarUnaFilaDespuesDelPie() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;OPE001;\nTOTAL;450.00;1\n"
				+ "2026-10-05;00000026;;350.00;PEN;OPE002;\n");

		assertThat(lectura.errores()).singleElement().satisfies(e -> assertThat(e.mensaje()).contains("después del pie"));
	}

	@Test
	void debeRechazarUnPieConLaCantidadDistinta() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;OPE001;\nTOTAL;450.00;2\n");

		assertThat(lectura.errores()).singleElement().satisfies(e -> assertThat(e.mensaje()).contains("dice 2 pagos"));
	}

	@Test
	void debeRechazarUnPieQueDifiereEnUnCentimo() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;OPE001;\n"
				+ "2026-10-05;00000026;;350.00;PEN;OPE002;\nTOTAL;800.01;2\n");

		assertThat(lectura.errores()).singleElement().satisfies(e -> assertThat(e.mensaje()).contains("no es la suma"));
	}

	@Test
	void debeAceptarElPieEnMinusculasYSinCantidad() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;OPE001;\ntotal;450.00;\n");

		assertThat(lectura.errores()).isEmpty();
		assertThat(lectura.totalDeclarado()).isEqualByComparingTo("450.00");
		assertThat(lectura.cantidadDeclarada()).isNull();
	}

	@Test
	void debeRechazarUnArchivoConSoloElPie() {
		assertThatThrownBy(() -> leer(CABECERA + "TOTAL;0.00;0\n")).isInstanceOf(ArchivoNoValidoException.class)
				.hasMessageContaining("no tiene pagos");
	}

	@Test
	void debeRechazarUnaCabeceraConUnaColumnaRepetida() {
		assertThatThrownBy(() -> leer("fecha_pago;codigo_alumno;monto;monto;moneda;numero_operacion\n"))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("dos veces");
	}

	@Test
	void debeTomarComoFechaDeProcesoLaUltimaFechaDePago() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;OPE001;\n"
				+ "2026-09-30;00000026;;350.00;PEN;OPE002;\n" + "2026-10-02;00000034;;350.00;PEN;OPE003;\n");

		assertThat(lectura.fechaProceso()).isEqualTo(LocalDate.of(2026, 10, 5));
	}

	@Test
	void debeNumerarLasLineasDeDatosSinContarLasFilasConError() {
		LecturaRecaudacion lectura = leer(CABECERA + "2026-10-05;00000018;;450.00;PEN;OPE001;\n"
				+ "2026-10-05;00000026;;mal;PEN;OPE002;\n" + "2026-10-05;00000034;;350.00;PEN;OPE003;\n");

		assertThat(lectura.filas()).extracting(FilaRecaudacion::numero).containsExactly(1, 2);
		assertThat(lectura.filas()).extracting(FilaRecaudacion::linea).containsExactly(2, 4);
	}

	@Test
	void debeRechazarUnCodigoDeAlumnoVacioOEnorme() {
		assertThat(unaLinea("2026-10-05;;;450.00;PEN;OPE001;").errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("falta el código de alumno"));
		assertThat(unaLinea("2026-10-05;123456789012345678901;;450.00;PEN;OPE001;").errores()).singleElement()
				.satisfies(e -> assertThat(e.mensaje()).contains("demasiado largo"));
	}
}
