package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import java.io.Serial;
import java.io.Serializable;

/** Un dato mal escrito en una línea del archivo del banco: el archivo no se registra mientras haya errores. */
public record ErrorFila(int linea, String mensaje) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
}
