package pe.edu.virgenmaria.cuentasclaras.academico.dto;

/** Sprint 9, tanda 1: una sección del año para Dirección, con su tutor vigente y cuántos docentes vigentes tiene. */
public record SeccionDelColegio(Long seccionId, String seccion, String tutor, int docentes, boolean activa) {
}
