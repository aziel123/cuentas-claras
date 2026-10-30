package pe.edu.virgenmaria.cuentasclaras.exportacion.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sprint 11, tanda 2 (sección 3.9): CSV en UTF-8 con BOM (Excel lo abre con tildes), coma y CRLF (RFC 4180). Fechas ISO 8601
 * en hora de Lima (como las guarda la base); dinero con su escala y punto ({@code 1250.00}). Neutraliza la inyección de
 * fórmulas (OWASP): una celda que empieza con {@code =}, {@code +}, {@code -}, {@code @}, tabulador o retorno lleva un
 * apóstrofo delante. No cierra el flujo de salida (lo cierra quien lo abrió).
 */
public final class EscritorCsv implements AutoCloseable {

	private static final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private final Writer escritor;

	private long filas;

	public EscritorCsv(OutputStream salida) {
		try {
			salida.write(BOM);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		this.escritor = new OutputStreamWriter(new java.io.FilterOutputStream(salida) {
			@Override
			public void close() throws IOException {
				flush();
			}
		}, StandardCharsets.UTF_8);
	}

	public void encabezado(List<String> columnas) {
		escribir(columnas.stream().map(c -> (Object) c).toList());
	}

	public void fila(List<?> valores) {
		escribir(valores);
		filas++;
	}

	/** Filas escritas (sin el encabezado). */
	public long filas() {
		return filas;
	}

	private void escribir(List<?> valores) {
		try {
			for (int i = 0; i < valores.size(); i++) {
				if (i > 0) {
					escritor.write(',');
				}
				escritor.write(celda(valores.get(i)));
			}
			escritor.write("\r\n");
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** El texto de una celda, neutralizado y entre comillas si hace falta. */
	public static String celda(Object valor) {
		if (valor == null) {
			return "";
		}
		String texto = switch (valor) {
			case BigDecimal b -> b.toPlainString();
			case Timestamp t -> t.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			case LocalDateTime t -> t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			case java.sql.Date d -> d.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
			case LocalDate d -> d.format(DateTimeFormatter.ISO_LOCAL_DATE);
			case Boolean b -> b ? "true" : "false";
			case Number n -> n.toString();
			default -> neutralizar(valor.toString());
		};
		if (texto.indexOf(',') >= 0 || texto.indexOf('"') >= 0 || texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0) {
			return '"' + texto.replace("\"", "\"\"") + '"';
		}
		return texto;
	}

	/** OWASP «CSV Injection»: lo que una hoja de cálculo leería como fórmula empieza con un apóstrofo. */
	public static String neutralizar(String texto) {
		if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0) {
			return "'" + texto;
		}
		return texto;
	}

	@Override
	public void close() {
		try {
			escritor.flush();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
