package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import java.util.List;
import java.util.Objects;

/** Una hoja de un reporte: nombre fijo (sin datos personales), encabezados y filas de {@link Celda}. */
public record HojaReporte(String nombre, List<String> encabezados, List<List<Celda>> filas) {

	public HojaReporte {
		Objects.requireNonNull(nombre, "nombre");
		if (nombre.isBlank() || nombre.length() > 31 || !nombre.matches("[\\p{L}\\p{N} ]+")) {
			throw new IllegalArgumentException("Nombre de hoja no válido: " + nombre);
		}
		encabezados = List.copyOf(encabezados);
		filas = List.copyOf(filas);
	}
}
