package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.time.LocalDateTime;

/** Sprint 9, tanda 1: una asignación en la pantalla de Docentes y secciones (Promotoría, Dirección y Administración). */
public record AsignacionVista(Long id, String docente, String docenteUsuario, String tipo, String areas, String estado,
		String variante, boolean vigente, String propuestoPor, String resueltoPor, LocalDateTime resueltoEn,
		String terminadaPor, String motivoFin) {
}
