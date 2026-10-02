package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import pe.edu.virgenmaria.cuentasclaras.alumnos.model.ImportacionAlumnos;

import java.io.Serial;
import java.io.Serializable;

/** Conteos de la revisión y del resultado. */
public record ResumenImportacion(int filas, int filasConErrores, int alumnosNuevos, int alumnosActualizados,
		int alumnosSinCambios, int apoderadosNuevos, int apoderadosActualizados, int familiasNuevas,
		int matriculasNuevas, int advertencias) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public boolean conErrores() {
		return filasConErrores > 0;
	}

	/** Nada que guardar: todo ya estaba registrado igual. */
	public boolean sinCambios() {
		return !conErrores() && alumnosNuevos == 0 && alumnosActualizados == 0 && apoderadosNuevos == 0
				&& apoderadosActualizados == 0 && matriculasNuevas == 0;
	}

	public ImportacionAlumnos.Conteos conteos() {
		return new ImportacionAlumnos.Conteos(filas, alumnosNuevos, alumnosActualizados, alumnosSinCambios,
				apoderadosNuevos, apoderadosActualizados, familiasNuevas, matriculasNuevas);
	}

	public String comoTexto() {
		return "filas=" + filas + "; alumnos nuevos=" + alumnosNuevos + "; actualizados=" + alumnosActualizados
				+ "; sin cambios=" + alumnosSinCambios + "; apoderados nuevos=" + apoderadosNuevos
				+ "; apoderados actualizados=" + apoderadosActualizados + "; familias nuevas=" + familiasNuevas
				+ "; matrículas nuevas=" + matriculasNuevas;
	}
}
