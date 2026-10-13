package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Ruc;

/**
 * Sprint 8, tanda 1 (sección 3.3): el RUC de un colegio tiene 11 dígitos, empieza con 10 (persona natural con negocio) o 20
 * (persona jurídica) y su dígito verificador es el de SUNAT (módulo 11, {@link Ruc}). V28 exige en la base el largo y el
 * prefijo; el verificador lo valida la aplicación.
 */
public final class ValidadorRuc {

	private ValidadorRuc() {
	}

	/** @return el RUC limpio (solo dígitos) */
	public static String validar(String escrito) {
		String ruc = escrito == null ? "" : escrito.replaceAll("[\\s-]", "");
		if (!ruc.matches("(10|20)[0-9]{9}")) {
			throw new ReglaNegocioException("El RUC debe tener 11 dígitos y empezar con 10 o 20.");
		}
		if (!Ruc.valido(ruc)) {
			throw new ReglaNegocioException("El RUC " + ruc + " no es válido: revisa el dígito final.");
		}
		return ruc;
	}

	public static boolean valido(String escrito) {
		try {
			validar(escrito);
			return true;
		}
		catch (ReglaNegocioException e) {
			return false;
		}
	}
}
