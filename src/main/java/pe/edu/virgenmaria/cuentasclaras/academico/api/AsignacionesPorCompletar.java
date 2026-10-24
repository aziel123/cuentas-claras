package pe.edu.virgenmaria.cuentasclaras.academico.api;

/**
 * Sprint 10, tanda 2 (sección 4 del brief): cuántas asignaciones vigentes del año en curso no tienen áreas del catálogo (las
 * del sprint 9): esos docentes no pueden poner notas. Lo lee la lista de puesta en marcha (sin nombres ni alumnos).
 */
public interface AsignacionesPorCompletar {

	int sinAreasDelAnioEnCurso();
}
