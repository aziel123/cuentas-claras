package pe.edu.virgenmaria.cuentasclaras.academico.dto;

/**
 * Sprint 9, tanda 1 (sección 3.3): un alumno en la lista de la sección que ve el docente. Solo su número de orden
 * (alfabético) y su nombre: nunca documento, fecha de nacimiento, apoderados, contactos ni nada de cobranza
 * ({@code DtoDocenteSinDatosTest}). Tampoco el estado de su matrícula: una reservada sin pagar no se distingue de una
 * activa (el docente no deduce quién debe).
 */
public record AlumnoDeSeccion(int numero, String apellidos, String nombres) {
}
