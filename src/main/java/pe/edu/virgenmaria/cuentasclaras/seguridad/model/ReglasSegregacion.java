package pe.edu.virgenmaria.cuentasclaras.seguridad.model;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Segregación de funciones (regla antifraude 1): quien cobra no aprueba.
 * <ul>
 *   <li>Todo usuario tiene al menos un rol.</li>
 *   <li>CAJA no se combina con PROMOTOR, DIRECTOR ni ADMINISTRACION.</li>
 *   <li>APODERADO no se combina con ningún otro rol: un trabajador que además es padre usa dos cuentas.</li>
 *   <li>DIRECTOR no se combina con ADMINISTRACION: quien registra descuentos no los aprueba
 *       (decisión por defecto, por confirmar con el colegio; diseño, sección 12).</li>
 *   <li>Sprint 9, tanda 1 (decisión 141): DOCENTE no se combina con CAJA ni ADMINISTRACION: esas cuentas ven quién debe y
 *       el docente nunca ve deudas. Con DIRECTOR sí (la pantalla de usuarios avisa que verá deudas por Dirección).</li>
 *   <li>Sprint 4: la cuenta APODERADO está enlazada a SU registro de apoderado (y por él, a su familia); ninguna otra
 *       cuenta lo está. Así el apoderado solo ve y paga lo de su familia.</li>
 * </ul>
 */
public final class ReglasSegregacion {

	private static final Set<Rol> INCOMPATIBLES_CON_CAJA = EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR, Rol.ADMINISTRACION);

	private ReglasSegregacion() {
	}

	public static void validar(Set<Rol> roles) {
		if (roles == null || roles.isEmpty()) {
			throw new ReglaNegocioException("El usuario debe tener al menos un rol.");
		}
		if (roles.contains(Rol.CAJA) && roles.stream().anyMatch(INCOMPATIBLES_CON_CAJA::contains)) {
			throw new ReglaNegocioException(
					"Caja no puede combinarse con Promotoría, Dirección ni Administración: quien cobra no aprueba.");
		}
		if (roles.contains(Rol.DIRECTOR) && roles.contains(Rol.ADMINISTRACION)) {
			throw new ReglaNegocioException(
					"Dirección no puede combinarse con Administración: quien registra descuentos no los aprueba.");
		}
		if (roles.contains(Rol.DOCENTE) && (roles.contains(Rol.CAJA) || roles.contains(Rol.ADMINISTRACION))) {
			throw new ReglaNegocioException(
					"Docente no puede combinarse con Caja ni Administración: el docente no ve deudas ni pagos.");
		}
		if (roles.contains(Rol.APODERADO) && roles.size() > 1) {
			throw new ReglaNegocioException(
					"Apoderado no puede combinarse con roles del personal. Crea una cuenta aparte para cada función.");
		}
		// Sprint 8, tanda 1: el operador de la plataforma no es de ningún colegio; solo y sin otro rol.
		if (roles.contains(Rol.OPERADOR_PLATAFORMA) && roles.size() > 1) {
			throw new ReglaNegocioException(
					"El operador de la plataforma no se combina con ningún rol de un colegio.");
		}
	}

	/** Los roles y, además, el enlace con el apoderado: obligatorio para APODERADO y prohibido para el personal. */
	/**
	 * Sprint 9, tanda 1 (decisión 141): DOCENTE con DIRECTOR se permite, pero esa persona verá deudas y pagos por su rol de
	 * Dirección. La pantalla de usuarios lo avisa.
	 */
	public static boolean docenteQueVeDeudas(java.util.Collection<Rol> roles) {
		return roles != null && roles.contains(Rol.DOCENTE) && (roles.contains(Rol.DIRECTOR) || roles.contains(Rol.PROMOTOR));
	}

	public static void validarCuenta(Set<Rol> roles, Long apoderadoId) {
		validar(roles);
		if (roles.contains(Rol.APODERADO) && apoderadoId == null) {
			throw new ReglaNegocioException("La cuenta de un apoderado se crea desde su ficha (Dar acceso en línea): queda "
					+ "enlazada a su familia.");
		}
		if (!roles.contains(Rol.APODERADO) && apoderadoId != null) {
			throw new ReglaNegocioException("Solo la cuenta de un apoderado se enlaza a una familia.");
		}
	}
}
