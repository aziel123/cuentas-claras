package pe.edu.virgenmaria.cuentasclaras.comun.archivo;

import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lector CSV propio, sin dependencias: separador {@code ;} o {@code ,} (el que más aparezca en la primera línea con
 * datos), campos entre comillas dobles (que pueden llevar el separador, saltos de línea y comillas escritas dos veces),
 * BOM de UTF-8 al inicio y filas vacías que se ignoran. Corta con error al pasar el máximo de filas. Puro.
 */
public final class LectorCsv {

	/** Una fila con datos: su número de línea en el archivo (desde 1) y sus celdas, sin espacios a los lados. */
	public record FilaCsv(int linea, List<String> celdas) {

		/** La celda de la columna (desde 0) o {@code ""} si la fila es más corta. */
		public String celda(int columna) {
			return columna >= 0 && columna < celdas.size() ? celdas.get(columna) : "";
		}
	}

	private LectorCsv() {
	}

	public static List<FilaCsv> leer(String texto, int maxFilas) {
		String contenido = texto == null ? "" : texto;
		if (!contenido.isEmpty() && contenido.charAt(0) == '﻿') {
			contenido = contenido.substring(1);
		}
		char separador = separador(contenido);
		List<FilaCsv> filas = new ArrayList<>();
		List<String> celdas = new ArrayList<>();
		StringBuilder celda = new StringBuilder();
		boolean entreComillas = false;
		int linea = 1;
		int lineaFila = 1;
		for (int i = 0; i < contenido.length(); i++) {
			char c = contenido.charAt(i);
			if (entreComillas) {
				if (c == '"') {
					if (i + 1 < contenido.length() && contenido.charAt(i + 1) == '"') {
						celda.append('"');
						i++;
					}
					else {
						entreComillas = false;
					}
				}
				else {
					if (c == '\n') {
						linea++;
					}
					celda.append(c);
				}
			}
			else if (c == '"' && celda.toString().isBlank()) {
				celda.setLength(0);
				entreComillas = true;
			}
			else if (c == separador) {
				celdas.add(celda.toString().strip());
				celda.setLength(0);
			}
			else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < contenido.length() && contenido.charAt(i + 1) == '\n') {
					i++;
				}
				celdas.add(celda.toString().strip());
				celda.setLength(0);
				agregar(filas, lineaFila, celdas, maxFilas);
				celdas = new ArrayList<>();
				linea++;
				lineaFila = linea;
			}
			else {
				celda.append(c);
			}
		}
		if (entreComillas) {
			throw new ArchivoNoValidoException("El archivo tiene unas comillas sin cerrar (desde la línea " + lineaFila
					+ "). Descárgalo otra vez del portal del banco.");
		}
		celdas.add(celda.toString().strip());
		agregar(filas, lineaFila, celdas, maxFilas);
		return Collections.unmodifiableList(filas);
	}

	private static void agregar(List<FilaCsv> filas, int linea, List<String> celdas, int maxFilas) {
		if (celdas.stream().allMatch(String::isEmpty)) {
			return;
		}
		if (filas.size() >= maxFilas) {
			throw new ArchivoNoValidoException("El archivo tiene más de " + String.format("%,d", maxFilas)
					+ " filas con datos. Divídelo, por ejemplo un archivo por día.");
		}
		filas.add(new FilaCsv(linea, List.copyOf(celdas)));
	}

	/** {@code ;} salvo que la primera línea con datos tenga más comas que punto y comas. */
	static char separador(String contenido) {
		for (String linea : contenido.split("\r?\n|\r", -1)) {
			if (linea.isBlank()) {
				continue;
			}
			long puntoYComa = linea.chars().filter(c -> c == ';').count();
			long comas = linea.chars().filter(c -> c == ',').count();
			return comas > puntoYComa ? ',' : ';';
		}
		return ';';
	}
}
