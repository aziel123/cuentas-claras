package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Lo leído del extracto: cómo se leyó (se guarda en el extracto), el número de cuenta que trae el archivo, los
 * movimientos en el orden del banco y los errores por línea.
 */
public record LecturaExtracto(String formato, String cuenta, List<FilaExtracto> filas, List<ErrorExtracto> errores)
		implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public LecturaExtracto {
		filas = List.copyOf(filas);
		errores = List.copyOf(errores);
	}
}
