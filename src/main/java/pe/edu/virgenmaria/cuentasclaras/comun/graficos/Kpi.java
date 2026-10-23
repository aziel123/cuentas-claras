package pe.edu.virgenmaria.cuentasclaras.comun.graficos;

/**
 * Una tarjeta de cifra: la etiqueta, la cifra grande, la comparación en palabras («12 % más que el 21/09») y su
 * tendencia, que se ve con una flecha Y con las palabras (nunca solo con color). {@code avance}: si la tarjeta lleva un
 * medidor, el % lleno como «64.00» para el atributo {@code width}; {@code null} si no lo lleva.
 */
public record Kpi(String etiqueta, String cifra, String comparacion, Tendencia tendencia, String avance, String enlace,
		String textoEnlace) {

	public Kpi(String etiqueta, String cifra, String comparacion, Tendencia tendencia) {
		this(etiqueta, cifra, comparacion, tendencia, null, null, null);
	}

	public enum Tendencia {

		SUBE("▲", "Sube"), BAJA("▼", "Baja"), IGUAL("=", "Igual"), NINGUNA("", "");

		private final String flecha;

		private final String texto;

		Tendencia(String flecha, String texto) {
			this.flecha = flecha;
			this.texto = texto;
		}

		public String flecha() {
			return flecha;
		}

		public String texto() {
			return texto;
		}
	}
}
