package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

/**
 * Correcciones del sprint 5 (S5-A1): un apoderado tiene un contacto nuevo o cambiado que su titular todavía no verificó.
 * La mensajería, en la misma transacción, le envía a ESE contacto el enlace de verificación de un solo uso (el token lo
 * genera el proceso de envío) y avisa a los demás apoderados activos de la familia. Mientras no se verifique, ese contacto
 * no recibe avisos ni enlaces.
 *
 * @param telefono       el celular por verificar ({@code null} si no hay que verificarlo)
 * @param correo         el correo por verificar ({@code null} si no hay que verificarlo)
 * @param apoderadoNuevo el apoderado se acaba de registrar (los demás reciben «se agregó un apoderado»)
 * @param avisarFamilia  avisar a los demás apoderados ({@code false} al reenviar la verificación)
 */
public record ContactoPorVerificar(Long apoderadoId, String telefono, String correo, boolean apoderadoNuevo,
		boolean avisarFamilia) {
}
