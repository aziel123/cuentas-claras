package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

import java.io.Serial;
import java.io.Serializable;

/** Un dato mal escrito en una línea del extracto: el extracto no se registra mientras haya errores. */
public record ErrorExtracto(int linea, String mensaje) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
}
