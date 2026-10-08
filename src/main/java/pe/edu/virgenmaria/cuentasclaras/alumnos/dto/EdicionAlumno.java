package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

/** Formulario de corrección precargado con los datos actuales del alumno. */
public record EdicionAlumno(CabeceraAlumno cabecera, ActualizarAlumnoRequest datos) {
}
