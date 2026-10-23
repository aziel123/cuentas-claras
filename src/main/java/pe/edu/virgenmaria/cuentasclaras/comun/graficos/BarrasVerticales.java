package pe.edu.virgenmaria.cuentasclaras.comun.graficos;

import java.util.List;

/**
 * Barras verticales (por ejemplo, lo cobrado en cada uno de los últimos 30 días). {@code guias}: las líneas del eje de
 * arriba hacia abajo, con su texto («S/ 2,000», «S/ 1,000», «S/ 0»). {@code rotulos}: unas pocas fechas bajo el eje.
 */
public record BarrasVerticales(String id, String titulo, String resumen, List<Guia> guias, List<Barra> barras,
		List<Rotulo> rotulos, List<String> columnas) {

	public BarrasVerticales {
		guias = List.copyOf(guias);
		barras = List.copyOf(barras);
		rotulos = List.copyOf(rotulos);
		columnas = List.copyOf(columnas);
	}

	/** {@code y}: la altura de la línea desde arriba, en % («0.00» es el tope). */
	public record Guia(String y, String texto) {
	}

	/**
	 * Una barra: {@code x}, {@code ancho}, {@code y} y {@code alto} en % del área del gráfico. {@code etiqueta} y
	 * {@code valor} van en su {@code <title>} y en la tabla; {@code detalle}, también en la tabla.
	 */
	public record Barra(String x, String ancho, String y, String alto, String etiqueta, String valor, String detalle) {
	}

	/** Una fecha bajo el eje, centrada en su barra ({@code x} en %), con su anclaje SVG (start, middle o end). */
	public record Rotulo(String x, String texto, String anclaje) {
	}
}
