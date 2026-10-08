package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** Plan completo. {@code huella}: SHA-256 de todo lo que se hará y del estado de la base que se tocará. */
public record PlanImportacion(List<FilaPlan> filas, ResumenImportacion resumen, String huella) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
}
