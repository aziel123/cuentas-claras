package pe.edu.virgenmaria.cuentasclaras.academico.dto;

/**
 * Sprint 10, tanda 1 (sección 3.4): una asignación VIGENTE de tutor o de área sin áreas del catálogo (las del sprint 9, con
 * el texto libre de entonces). Ese docente marca asistencia, pero no califica hasta que se proponga otra con sus áreas.
 */
public record AsignacionSinAreas(Long id, String docente, String tipo, String seccion, String areasTexto) {
}
