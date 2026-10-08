package pe.edu.virgenmaria.cuentasclaras.comun.excel;

import java.io.Serial;
import java.io.Serializable;

/**
 * Una celda leída. {@code valorCrudo} es el texto tal cual (de una celda numérica, el contenido de {@code <v>}, sin
 * pasar por {@code double}). {@code formula}: la celda tiene una fórmula ({@code <f>}); no se usa su valor.
 */
public record CeldaXlsx(String referencia, TipoCelda tipo, String valorCrudo, boolean formula) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public boolean vacia() {
		return !formula && (valorCrudo == null || valorCrudo.isBlank());
	}
}
