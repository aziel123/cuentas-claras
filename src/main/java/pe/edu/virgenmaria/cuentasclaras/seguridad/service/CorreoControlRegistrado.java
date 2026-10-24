package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

/**
 * Sprint 11, tanda 1 (AP-2): ¿el colegio del contexto tiene registrado su correo externo de control? Sin él no hay
 * facultad del titular (el testigo independiente recibe cada uso). Puerto: lo implementa la mensajería (dueña de
 * {@code configuracion_colegio}); seguridad no depende de comunicacion.
 */
public interface CorreoControlRegistrado {

	boolean registrado();

	/** Sprint 11, tanda 1c (A1c-8): si ese correo es el correo externo de control del colegio en contexto. */
	boolean esElCorreoDeControl(String correo);
}
