package pe.edu.virgenmaria.cuentasclaras.comun.graficos;

import java.util.List;

/** Barras horizontales, una por fila (por ejemplo, la morosidad de cada grado), en escala de 0 a 100 %. */
public record BarrasHorizontales(String id, String titulo, String resumen, List<Fila> filas, List<String> columnas,
		String vacio) {

	public BarrasHorizontales {
		filas = List.copyOf(filas);
		columnas = List.copyOf(columnas);
	}

	/** {@code ancho}: el largo de la barra en % («25.00»). {@code valor}: el texto junto a la barra («25 %»). */
	public record Fila(String etiqueta, String ancho, String valor, String detalle, String monto) {
	}
}
