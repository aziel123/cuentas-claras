package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

/**
 * Sprint 11, tanda 1 (AP-2): ¿el colegio del contexto tiene registrado su correo externo de control? Sin él no hay
 * facultad del titular (el testigo independiente recibe cada uso). Puerto: lo implementa la mensajería (dueña de
 * {@code configuracion_colegio}); seguridad no depende de comunicacion.
 */
public interface CorreoControlRegistrado {

	boolean registrado();
}
