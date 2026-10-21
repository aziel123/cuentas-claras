package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.time.LocalTime;

/**
 * Sprint 9, tanda 2: un alumno en la hoja del día. Su número de orden, su nombre y su estado (y, si ya se registró, la
 * marca vigente y si se puede corregir hoy). Nunca documentos, contactos ni nada de cobranza
 * ({@code DtoDocenteSinDatosTest}). {@code nota}: la de una justificación, SOLO para el tutor(a) (E35); para los demás,
 * {@code null}.
 */
public record AlumnoEnHoja(int numero, long alumnoId, String apellidos, String nombres, String estado, String etiqueta,
		String variante, LocalTime horaLlegada, Long marcaId, String origen, boolean corregibleHoy, boolean justificada, String nota) {
}
