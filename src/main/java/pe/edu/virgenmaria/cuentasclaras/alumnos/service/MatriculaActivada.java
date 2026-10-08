package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

/**
 * Sprint 5: una matrícula RESERVADA pasó a ACTIVA (su cuota de matrícula quedó pagada). Se publica en la misma
 * transacción: {@code cobranza} genera ahí las pensiones que faltan (si falla, la activación tampoco queda).
 */
public record MatriculaActivada(Long matriculaId) {
}
