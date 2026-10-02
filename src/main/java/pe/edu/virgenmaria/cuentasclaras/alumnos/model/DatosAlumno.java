package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import java.time.LocalDate;

/** Datos del alumno ya validados y normalizados por {@link ReglasDatosPersonales}. */
public record DatosAlumno(DocumentoIdentidad documento, String apellidoPaterno, String apellidoMaterno, String nombres,
		LocalDate fechaNacimiento) {
}
