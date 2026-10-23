package pe.edu.virgenmaria.cuentasclaras.academico.dto;

/**
 * Sprint 9, tanda 1: una sección con asignación VIGENTE del docente en sesión. {@code funcion}: «Tutor(a)», «Docente de
 * área: Matemática» o «Auxiliar de educación» (varias, separadas por « · », si tiene más de una en la sección).
 */
public record SeccionDocente(Long seccionId, String seccion, int anio, String funcion, boolean tutor) {
}
