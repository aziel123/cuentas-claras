package pe.edu.virgenmaria.cuentasclaras.alumnos.dto;

/** Matrícula registrada. {@code advertencia}: aviso de edad que no corresponde al grado (no bloquea), o null. */
public record MatriculaResultado(Long matriculaId, Long alumnoId, String advertencia) {
}
