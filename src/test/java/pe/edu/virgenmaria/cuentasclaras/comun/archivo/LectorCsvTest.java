package pe.edu.virgenmaria.cuentasclaras.comun.archivo;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.archivo.LectorCsv.FilaCsv;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El lector CSV propio (sin dependencias) y la primera barrera ante un archivo de texto subido. */
class LectorCsvTest {

	@Test
	void comillasYSeparadores() {
		List<FilaCsv> filas = LectorCsv.leer("a;b;c\n\"uno;dos\";\"con \"\"comillas\"\"\";  tres  \r\n"
				+ "\"varias\nlíneas\";x;y", 10);

		assertThat(filas).hasSize(3);
		assertThat(filas.get(1).celdas()).containsExactly("uno;dos", "con \"comillas\"", "tres");
		assertThat(filas.get(1).linea()).isEqualTo(2);
		assertThat(filas.get(2).celda(0)).isEqualTo("varias\nlíneas");
		assertThat(filas.get(2).celda(5)).isEmpty();
		// Con comas como separador (la primera línea con datos decide).
		assertThat(LectorCsv.leer("a,b\n1,2", 10).get(1).celdas()).containsExactly("1", "2");
	}

	@Test
	void bomUtf8() {
		byte[] conBom = ("﻿fecha_pago;monto\n2026-10-01;450.00").getBytes(StandardCharsets.UTF_8);
		String texto = ValidadorArchivoPlano.validar("banco.csv", conBom, 100);

		assertThat(texto).startsWith("fecha_pago");
		assertThat(LectorCsv.leer("﻿a;b", 10).getFirst().celdas()).containsExactly("a", "b");
	}

	@Test
	void filasVaciasSeIgnoran() {
		List<FilaCsv> filas = LectorCsv.leer("a;b\n\n;;\n   \n1;2\n", 10);

		assertThat(filas).extracting(FilaCsv::linea).containsExactly(1, 5);
	}

	@Test
	void lineasDeMasSeRechazan() {
		assertThatThrownBy(() -> LectorCsv.leer("a\nb\nc\nd", 3)).isInstanceOf(ArchivoNoValidoException.class)
				.hasMessageContaining("más de 3 filas");
		byte[] largo = "x\n".repeat(20).getBytes(StandardCharsets.UTF_8);
		assertThatThrownBy(() -> ValidadorArchivoPlano.validar("banco.csv", largo, 5))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("más de 5 líneas");
	}

	@Test
	void comillaSinCerrarEsError() {
		assertThatThrownBy(() -> LectorCsv.leer("a;\"b\n1;2", 10)).isInstanceOf(ArchivoNoValidoException.class)
				.hasMessageContaining("comillas sin cerrar");
	}

	/** Un binario disfrazado de CSV, otra extensión o un archivo de más de 2 MB no llegan al lector. */
	@Test
	void binarioOtraExtensionYDemasiadoGrandeSeRechazan() {
		assertThatThrownBy(() -> ValidadorArchivoPlano.validar("banco.csv", new byte[] { 'a', 0, 'b' }, 10))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("no son de texto");
		assertThatThrownBy(() -> ValidadorArchivoPlano.validar("banco.exe", "a;b".getBytes(), 10))
				.isInstanceOf(ArchivoNoValidoException.class);
		assertThatThrownBy(() -> ValidadorArchivoPlano.exigirTamano(ArchivoCargado.MAXIMO_BYTES + 1L))
				.isInstanceOf(ArchivoNoValidoException.class).hasMessageContaining("2 MB");
	}

	/** Muchos bancos exportan en ISO-8859-1: se lee igual (las tildes no se pierden). */
	@Test
	void isoLatin1SeLee() {
		byte[] latin = "canal;Agente Ñaña\n".getBytes(StandardCharsets.ISO_8859_1);

		assertThat(ValidadorArchivoPlano.validar("banco.txt", latin, 10)).contains("Ñaña");
	}
}
