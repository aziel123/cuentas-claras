package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lector en streaming endurecido, con libros escritos a mano para controlar cada celda. */
class LectorXlsxSeguroTest {

	private final LectorXlsxSeguro lector = new LectorXlsxSeguro(PropiedadesExcel.porDefecto());

	/**
	 * Auditoría B3: un archivo de pocos KB puede declarar cientos de miles de textos vacíos que POI cargaría en memoria.
	 * Se cuentan en streaming y se corta al pasar el máximo.
	 */
	@Test
	void demasiadosTextosCompartidosSonRechazados() {
		LectorXlsxSeguro conLimite = new LectorXlsxSeguro(new PropiedadesExcel(org.springframework.util.unit.DataSize
				.ofMegabytes(2), 2000, org.springframework.util.unit.DataSize.ofMegabytes(20), 200, 100));
		byte[] justo = XlsxDePrueba.crudo("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>", false,
				"<si><t>x</t></si>".repeat(100), null);
		byte[] demasiados = XlsxDePrueba.crudo("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>", false,
				"<si><t/></si>".repeat(101), null);

		assertThat(conLimite.leer(justo, "Alumnos", 10, 17).filas()).hasSize(1);
		assertThatThrownBy(() -> conLimite.leer(demasiados, "Alumnos", 10, 17))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("demasiados textos distintos (más de 100)");
		assertThat(PropiedadesExcel.porDefecto().maxTextosCompartidos()).isEqualTo(20000);
	}

	@Test
	void leeTextoCompartidoEInline() {
		byte[] libro = XlsxDePrueba.crudo("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c>"
				+ "<c r=\"B1\" t=\"inlineStr\"><is><t>Huamán</t><rPh sb=\"0\" eb=\"1\"><t>FONETICA</t></rPh></is></c>"
				+ "<c r=\"C1\" t=\"s\"><v>1</v></c></row>", false,
				"<si><t>Quispe</t></si><si><r><t>Ma</t></r><r><t>teo</t></r><rPh sb=\"0\" eb=\"1\"><t>X</t></rPh></si>", null);

		FilaXlsx fila = lector.leer(libro, "Alumnos", 10, 17).filas().getFirst();

		assertThat(fila.celda(0).valorCrudo()).isEqualTo("Quispe");
		assertThat(fila.celda(1).valorCrudo()).isEqualTo("Huamán");
		assertThat(fila.celda(2).valorCrudo()).isEqualTo("Mateo");
		assertThat(fila.celda(1).tipo()).isEqualTo(TipoCelda.TEXTO);
	}

	@Test
	void marcaCeldasConFormula() {
		byte[] libro = XlsxDePrueba.libro("Alumnos", List.of(
				List.of(new XlsxDePrueba.Formula("\"Ana\"&\" Paz\""), new XlsxDePrueba.Formula("1+1"), "texto")));

		FilaXlsx fila = lector.leer(libro, "Alumnos", 10, 17).filas().getFirst();

		assertThat(fila.celda(0).formula()).isTrue();
		assertThat(fila.celda(1).formula()).isTrue();
		assertThat(fila.celda(2).formula()).isFalse();
	}

	@Test
	void leeFechaSerialYFechaTexto() {
		HojaLeida hoja = lector.leer(XlsxDePrueba.libro("Alumnos", List.of(List.of(42000, "05/03/2015"))), "Alumnos",
				10, 17);

		assertThat(hoja.fecha1904()).isFalse();
		FilaXlsx fila = hoja.filas().getFirst();
		assertThat(ValoresXlsx.fecha(fila.celda(0), hoja.fecha1904())).contains(LocalDate.of(2014, 12, 27));
		assertThat(fila.celda(1).tipo()).isEqualTo(TipoCelda.TEXTO);
		assertThat(fila.celda(1).valorCrudo()).isEqualTo("05/03/2015");
	}

	@Test
	void respetaElSistemaDeFechas1904() {
		HojaLeida hoja = lector.leer(XlsxDePrueba.crudo("<row r=\"1\"><c r=\"A1\"><v>42000</v></c></row>", true, null,
				null), "Alumnos", 10, 17);

		assertThat(hoja.fecha1904()).isTrue();
		assertThat(ValoresXlsx.fecha(hoja.filas().getFirst().celda(0), true)).contains(LocalDate.of(2018, 12, 28));
	}

	@Test
	void ignoraFilasVacias() {
		HojaLeida hoja = lector.leer(XlsxDePrueba.crudo("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>A</t></is></c></row>"
				+ "<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is><t>  </t></is></c><c r=\"B2\"/></row>"
				+ "<row r=\"3\"></row>"
				+ "<row r=\"7\"><c r=\"B7\"><v>5</v></c></row>"), "Alumnos", 10, 17);

		assertThat(hoja.filas()).extracting(FilaXlsx::numero).containsExactly(1, 7);
	}

	@Test
	void seDetieneAlSuperarElMaximoDeFilas() {
		StringBuilder filas = new StringBuilder();
		for (int i = 1; i <= 50; i++) {
			filas.append("<row r=\"").append(i).append("\"><c r=\"A").append(i).append("\"><v>").append(i)
					.append("</v></c></row>");
		}
		assertThatThrownBy(() -> lector.leer(XlsxDePrueba.crudo(filas.toString()), "Alumnos", 11, 17))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("más de 10 filas de datos");
	}

	@Test
	void rechazaDoctypeXxe() {
		byte[] libro = XlsxDePrueba.crudo("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>&xxe;</t></is></c></row>",
				false, null, "<!DOCTYPE w [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]>");
		assertThatThrownBy(() -> lector.leer(libro, "Alumnos", 10, 17))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessage(LectorXlsxSeguro.MENSAJE_NO_PERMITIDO);
	}

	@Test
	void enteroSeLeeSinDecimalesNiNotacionCientifica() {
		FilaXlsx fila = lector.leer(XlsxDePrueba.crudo("<row r=\"1\"><c r=\"A1\"><v>1.234567E6</v></c>"
				+ "<c r=\"B1\"><v>12345678</v></c><c r=\"C1\"><v>7.5</v></c><c r=\"D1\"><v>987654321.0</v></c></row>"),
				"Alumnos", 10, 17).filas().getFirst();

		assertThat(ValoresXlsx.entero(fila.celda(0))).contains("1234567");
		assertThat(ValoresXlsx.entero(fila.celda(1))).contains("12345678");
		assertThat(ValoresXlsx.entero(fila.celda(2))).isEmpty();
		assertThat(ValoresXlsx.entero(fila.celda(3))).contains("987654321");
	}

	@Test
	void celdaConErrorDeExcelSeReporta() {
		FilaXlsx fila = lector.leer(XlsxDePrueba.libro("Alumnos", List.of(List.of(new XlsxDePrueba.ErrorExcel()))),
				"Alumnos", 10, 17).filas().getFirst();
		assertThat(fila.celda(0).tipo()).isEqualTo(TipoCelda.ERROR);
		assertThat(fila.celda(0).valorCrudo()).isEqualTo("#N/A");
	}

	@Test
	void columnasDeMasSeIgnoranYSeInforman() {
		HojaLeida hoja = lector.leer(XlsxDePrueba.libro("Alumnos", List.of(List.of("a", "b", "c", "d"))), "Alumnos",
				10, 2);
		assertThat(hoja.filas().getFirst().celdas()).containsOnlyKeys(0, 1);
		assertThat(hoja.columnasIgnoradas()).containsExactly(2, 3);
	}

	@Test
	void zipBombYHojaFaltanteSonRechazadosTambienAqui() {
		byte[] libro = XlsxDePrueba.libro("Alumnos", List.of(List.of("a")));
		assertThatThrownBy(() -> lector.leer(XlsxDePrueba.zipBomb(libro, 60), "Alumnos", 10, 17))
				.isInstanceOf(ArchivoNoValidoException.class);
		assertThatThrownBy(() -> lector.leer(libro, "Otra", 10, 17))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("no tiene la hoja «Otra»");
		assertThatThrownBy(() -> lector.leer("no es un zip".getBytes(), "Alumnos", 10, 17))
				.isInstanceOf(ArchivoNoValidoException.class);
	}
}
