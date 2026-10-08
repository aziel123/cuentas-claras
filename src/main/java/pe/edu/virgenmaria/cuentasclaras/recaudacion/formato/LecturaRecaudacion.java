package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo leído del archivo del banco.
 *
 * @param formato           cómo se leyó (por ejemplo {@code GENERICO_CSV}); se guarda en el lote
 * @param fechaProceso      fecha de proceso del banco (la última fecha de pago si el archivo no la trae)
 * @param totalDeclarado    el total del pie del archivo, si lo trae (debe ser la suma de las líneas)
 * @param cantidadDeclarada la cantidad del pie, si la trae
 */
public record LecturaRecaudacion(String formato, LocalDate fechaProceso, BigDecimal totalDeclarado,
		Integer cantidadDeclarada, List<FilaRecaudacion> filas, List<ErrorFila> errores) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public boolean conErrores() {
		return !errores.isEmpty();
	}
}
