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
}
