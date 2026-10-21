package pe.edu.virgenmaria.cuentasclaras.comun.graficos;

import java.util.List;

/**
 * Una barra apilada al 100 % (por ejemplo, lo cobrado en el mes por medio de pago). Cada segmento lleva su clase de color
 * (en un orden fijo, nunca por tamaño), su leyenda con el monto y el % escritos y su fila en la tabla.
 */
public record BarraApilada(String id, String titulo, String resumen, String total, List<Segmento> segmentos,
		List<String> columnas, String vacio) {

	public BarraApilada {
		segmentos = List.copyOf(segmentos);
		columnas = List.copyOf(columnas);
	}

	public boolean sinDatos() {
		return segmentos.stream().allMatch(s -> "0.00".equals(s.ancho()));
	}

	/** {@code x} y {@code ancho} en % del largo de la barra. {@code serie}: 1, 2 o 3 (la clase de color). */
	public record Segmento(int serie, String etiqueta, String x, String ancho, String monto, String porcentaje,
			String pagos) {
	}
}
