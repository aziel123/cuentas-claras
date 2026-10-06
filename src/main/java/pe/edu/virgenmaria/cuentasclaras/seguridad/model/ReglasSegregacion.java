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
		if (roles.contains(Rol.APODERADO) && roles.size() > 1) {
			throw new ReglaNegocioException(
					"Apoderado no puede combinarse con roles del personal. Crea una cuenta aparte para cada función.");
		}
	}

	/** Los roles y, además, el enlace con el apoderado: obligatorio para APODERADO y prohibido para el personal. */
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
