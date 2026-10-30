package pe.edu.virgenmaria.cuentasclaras.exportacion;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.EscritorCsv;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.ZipCifrado;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (3.9): 50,000 filas pasan en flujo por el CSV y el ZIP cifrado (AES-256), sin juntarse en memoria:
 * el archivo se abre con la clave, trae las 50,000 filas y su SHA-256 es la del manifiesto. Sin la clave no se abre.
 */
class ExportacionGrandeTest {

	private static final int FILAS = 50_000;

	@TempDir
	Path directorio;

	@Test
	void cincuentaMilFilasEnFlujo() throws Exception {
		String clave = ZipCifrado.nuevaClave();
		assertThat(clave).hasSize(24).doesNotContain("0", "O", "1", "l", "I");
		assertThat(ZipCifrado.agrupada(clave)).matches("(.{4}-){5}.{4}");
		Path archivo = directorio.resolve("grande.zip");
		ZipCifrado.Entrada entrada;
		try (ZipCifrado zip = new ZipCifrado(archivo, clave)) {
			entrada = zip.agregar("datos/pago.csv", 0, salida -> {
				try (EscritorCsv csv = new EscritorCsv(salida)) {
					csv.encabezado(List.of("id", "colegio_id", "total", "detalle"));
					for (long i = 1; i <= FILAS; i++) {
						csv.fila(List.of(i, 1L, new BigDecimal("450.00"), "Pensión de marzo " + i));
					}
				}
			});
		}
		assertThat(entrada.bytes()).isGreaterThan(FILAS * 20L);

		long lineas = 0;
		MessageDigest sha = MessageDigest.getInstance("SHA-256");
		try (ZipFile zip = new ZipFile(archivo.toFile(), clave.toCharArray())) {
			assertThat(zip.isEncrypted()).isTrue();
			FileHeader cabecera = zip.getFileHeader("datos/pago.csv");
			try (InputStream entradaZip = zip.getInputStream(cabecera)) {
				byte[] todo = entradaZip.readAllBytes();
				sha.update(todo);
				try (BufferedReader lector = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(todo),
						StandardCharsets.UTF_8))) {
					while (lector.readLine() != null) {
						lineas++;
					}
				}
			}
		}
		assertThat(lineas).isEqualTo(FILAS + 1L);
		assertThat(HexFormat.of().formatHex(sha.digest())).isEqualTo(entrada.sha256());

		// Con otra clave no se lee.
		try (ZipFile otra = new ZipFile(archivo.toFile(), "otra-clave-cualquiera-xx".toCharArray())) {
			org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
				try (InputStream in = otra.getInputStream(otra.getFileHeader("datos/pago.csv"))) {
					in.readAllBytes();
				}
			}).isInstanceOf(Exception.class);
		}
	}
}
