package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Plan de una fila: qué se hará al confirmar. Los ids son los de la base al momento de planificar; al confirmar se
 * vuelve a planificar (con el año bloqueado) y se compara la huella del plan.
 *
 * @param alumnoId           alumno ya registrado (null si es nuevo)
 * @param apoderadoId        apoderado ya registrado (null si es nuevo)
 * @param seccionId          sección del año donde se matricula
 * @param actualizarAlumno   corrige nombres o fecha de nacimiento
 * @param actualizarApoderado corrige los datos del apoderado (solo en la primera fila de ese apoderado)
 * @param cambiarResponsable el responsable de pago pasa a ser este apoderado (de la misma familia)
 * @param matricular         se crea la matrícula del año
 */
public record FilaPlan(FilaImportacion fila, Clasificacion clasificacion, List<CambioFila> cambios,
		List<ErrorFila> errores, Long alumnoId, Long apoderadoId, Long seccionId, boolean actualizarAlumno,
		boolean actualizarApoderado, boolean cambiarResponsable, boolean matricular) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public boolean conErrores() {
		return !errores.isEmpty();
	}
}
