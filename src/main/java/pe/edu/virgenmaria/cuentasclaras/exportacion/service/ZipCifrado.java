package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sprint 11, tanda 2 (decisiones 198 y 199): el ZIP de la exportación, cifrado con AES-256 (WinZip AE-2, zip4j), escrito en
 * flujo: cada archivo entra sin pasar entero por la memoria y se anota con sus bytes y su SHA-256 para el manifiesto. Se
 * abre con 7-Zip o con el Explorador de Windows 11. Tope de 2 GB.
 */
public final class ZipCifrado implements AutoCloseable {

	/** Un archivo del ZIP, para {@code MANIFIESTO.csv}. */
	public record Entrada(String archivo, long filas, long bytes, String sha256) {
	}

	public static final long TOPE_BYTES = 2L * 1024 * 1024 * 1024;

	/** Sin 0/O ni 1/l/I (se dicta y se copia sin dudas). */
	private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

	private static final SecureRandom AZAR = new SecureRandom();

	private final ZipOutputStream zip;

	private final List<Entrada> entradas = new ArrayList<>();

	private long total;

	private boolean cerrado;

	public ZipCifrado(Path archivo, String clave) {
		try {
			this.zip = new ZipOutputStream(Files.newOutputStream(archivo), clave.toCharArray());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** La clave: 24 caracteres (6 grupos de 4 al mostrarla). */
	public static String nuevaClave() {
		StringBuilder clave = new StringBuilder(24);
		for (int i = 0; i < 24; i++) {
			clave.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
		}
		return clave.toString();
	}

	/** «AbCd-EfGh-…»: como la ve el titular. */
	public static String agrupada(String clave) {
		return String.join("-", clave.split("(?<=\\G.{4})"));
	}

	/** Escribe un archivo con lo que hace {@code escritura}; devuelve su entrada (bytes y SHA-256 del contenido). */
	public Entrada agregar(String nombre, long filas, Consumer<OutputStream> escritura) {
		ZipParameters p = new ZipParameters();
		p.setFileNameInZip(nombre);
		p.setEncryptFiles(true);
		p.setEncryptionMethod(EncryptionMethod.AES);
		p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
		p.setCompressionLevel(CompressionLevel.NORMAL);
		try {
			zip.putNextEntry(p);
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			long[] bytes = { 0 };
			OutputStream contado = new FilterOutputStream(zip) {
				@Override
				public void write(int b) throws IOException {
					zip.write(b);
					sha.update((byte) b);
					sumar(bytes, 1);
				}

				@Override
				public void write(byte[] b, int desde, int largo) throws IOException {
					zip.write(b, desde, largo);
					sha.update(b, desde, largo);
					sumar(bytes, largo);
				}

				@Override
				public void close() throws IOException {
					flush();
				}
			};
			escritura.accept(contado);
			contado.flush();
			zip.closeEntry();
			Entrada entrada = new Entrada(nombre, filas, bytes[0], HexFormat.of().formatHex(sha.digest()));
			entradas.add(entrada);
			return entrada;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public Entrada agregar(String nombre, byte[] contenido) {
		return agregar(nombre, 0, salida -> {
			try {
				salida.write(contenido);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	private void sumar(long[] bytes, long n) {
		bytes[0] += n;
		total += n;
		if (total > TOPE_BYTES) {
			throw new ExportacionDemasiadoGrandeException();
		}
	}

	public List<Entrada> entradas() {
		return List.copyOf(entradas);
	}

	@Override
	public void close() {
		if (cerrado) {
			return;
		}
		cerrado = true;
		try {
			zip.close();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** El colegio supera el tope de 2 GB: se parte (se decide cuando pase, sección 8 del brief). */
	public static final class ExportacionDemasiadoGrandeException extends RuntimeException {

		ExportacionDemasiadoGrandeException() {
			super("La exportación supera el tope de 2 GB");
		}
	}
}
