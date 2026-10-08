package pe.edu.virgenmaria.cuentasclaras.comun.archivo;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.LectorCsv.FilaCsv;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** QA del sprint 4: bordes del lector CSV propio (saltos de línea, comillas, separador y codificación). Puro. */
class LectorCsvBordesTest {

	@Test
	void debeAceptarSaltosDeLineaSoloConRetornoDeCarro() {
		List<FilaCsv> filas = LectorCsv.leer("a;b\r1;2\r3;4", 10);

		assertThat(filas).extracting(FilaCsv::linea).containsExactly(1, 2, 3);
		assertThat(filas.get(2).celdas()).containsExactly("3", "4");
	}

	@Test
	void debeNumerarBienLaFilaQueSigueAUnaCeldaConSaltoDeLinea() {
		List<FilaCsv> filas = LectorCsv.leer("a;b\n\"uno\ndos\ntres\";x\nfin;y\n", 10);

		assertThat(filas).extracting(FilaCsv::linea).containsExactly(1, 2, 5);
	}

	@Test
	void debeDecidirElSeparadorPorLaPrimeraLineaConDatosAunqueHayaLineasEnBlancoAntes() {
		List<FilaCsv> filas = LectorCsv.leer("\n\n  \na,b,c\n1,2,3\n", 10);

		assertThat(filas.getLast().celdas()).containsExactly("1", "2", "3");
	}

	@Test
	void debeConservarElPuntoYComaDentroDeUnaCeldaEntreComillasConComaComoSeparador() {
		List<FilaCsv> filas = LectorCsv.leer("a,b\n\"x;y\",\"1,250.00\"\n", 10);

		assertThat(filas.get(1).celdas()).containsExactly("x;y", "1,250.00");
	}

	@Test
	void debeQuitarLosEspaciosAlrededorDeUnaCeldaEntreComillas() {
		List<FilaCsv> filas = LectorCsv.leer("a;b\n  \"450.00\"  ;  x  \n", 10);

		assertThat(filas.get(1).celdas()).containsExactly("450.00", "x");
	}

	@Test
	void debeIgnorarUnaFilaDeCeldasEntreComillasVacias() {
		List<FilaCsv> filas = LectorCsv.leer("a;b\n\"\";\"\"\n1;2\n", 10);

		assertThat(filas).extracting(FilaCsv::linea).containsExactly(1, 3);
	}

	@Test
	void debeContarSoloLasFilasConDatosParaElMaximo() {
		assertThat(LectorCsv.leer("a\n\n\n\nb\n\n\nc\n", 3)).hasSize(3);
	}

	@Test
	void debeQuitarElBomAunqueElArchivoVengaEnIsoLatin1Valido() {
		// BOM UTF-8 seguido de una «Ñ» en ISO-8859-1 (bytes que no son UTF-8 válido): se lee como ISO-8859-1 sin el BOM.
		byte[] mezcla = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', ';', (byte) 0xD1 };

		String texto = ValidadorArchivoPlano.validar("banco.csv", mezcla, 10);

		assertThat(texto).isEqualTo("a;Ñ");
	}

	@Test
	void debeLeerUnaCeldaFinalSinSaltoDeLinea() {
		List<FilaCsv> filas = LectorCsv.leer("a;b\n1;\"2\"", 10);

		assertThat(filas.get(1).celdas()).containsExactly("1", "2");
	}

	@Test
	void debeLeerUnTextoUtf8ConTildes() {
		byte[] utf8 = "canal;Agente Ñaña\n".getBytes(StandardCharsets.UTF_8);

		assertThat(ValidadorArchivoPlano.validar("banco.csv", utf8, 10)).contains("Ñaña");
	}
}
