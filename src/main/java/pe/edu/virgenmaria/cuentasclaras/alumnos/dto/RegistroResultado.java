package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

/** Alumno registrado (y matriculado si se eligió sección). {@code advertencia}: aviso de edad, o null. */
public record RegistroResultado(Long alumnoId, Long familiaId, String advertencia) {
}
