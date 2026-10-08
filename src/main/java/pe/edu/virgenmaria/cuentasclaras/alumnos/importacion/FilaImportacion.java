package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Una fila del Excel ya validada con {@code ReglasDatosPersonales}. Si tiene errores, los datos pueden venir
 * incompletos (null) y la fila no se importa.
 */
public record FilaImportacion(int fila, DatosAlumno alumno, DatosApoderado apoderado, Grado grado, String seccion,
		List<ErrorFila> errores, List<String> advertencias) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public boolean valida() {
		return errores.isEmpty();
	}

	/** Nombre para mostrar en la revisión (vacío si no se pudo leer). */
	public String nombreAlumno() {
		return alumno == null ? "" : alumno.nombres() + " " + alumno.apellidoPaterno()
				+ (alumno.apellidoMaterno() == null ? "" : " " + alumno.apellidoMaterno());
	}
}
