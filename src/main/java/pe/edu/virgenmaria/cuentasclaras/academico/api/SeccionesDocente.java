package pe.edu.virgenmaria.cuentasclaras.academico.api;

import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDocente;

import java.util.List;

/**
 * Sprint 9 (sección 9): las secciones del docente en sesión, para los módulos que lo necesitan (los comunicados de la
 * tanda 3). Solo lectura: ids y etiquetas, nunca datos de los alumnos.
 */
public interface SeccionesDocente {

	/** Las secciones con una asignación VIGENTE del docente en sesión (de años no cerrados). */
	List<SeccionDocente> mias();

	/**
	 * Exige que el docente en sesión tenga una asignación VIGENTE en la sección ({@code conAuxiliar}: también vale la de
	 * auxiliar). Si no, la sección «no existe» para él: 404 (como el IDOR de siempre).
	 */
	void exigir(long seccionId, boolean conAuxiliar);

	/**
	 * QA-S9-9: si ese docente (no quien está en sesión: lo pregunta quien aprueba su comunicado) sigue con una asignación
	 * VIGENTE de tutor o de área (no auxiliar) en la sección, en un año no cerrado.
	 */
	boolean sigueAsignado(long usuarioId, long seccionId);

	/**
	 * Sprint 10, tanda 1 (sección 3.4): las áreas del catálogo (ids) que el docente en sesión califica en la sección: las de
	 * sus asignaciones VIGENTES de tutor o de área, sin cobertura. Vacía si no tiene ninguna (las del sprint 9 sin áreas del
	 * catálogo no califican hasta que se vuelvan a proponer). Las transversales del tutor las agrega la tanda 2.
	 */
	java.util.List<Long> areasDe(long seccionId);
}
