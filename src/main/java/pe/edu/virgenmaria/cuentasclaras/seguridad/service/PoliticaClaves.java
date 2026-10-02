package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de una clave nueva. Se prefiere una frase larga y fácil de recordar a una clave corta
 * con símbolos.
 * <ul>
 *   <li>Entre {@value #MINIMO} y {@value #MAXIMO} caracteres.</li>
 *   <li>Como máximo {@value #MAXIMO_BYTES} bytes en UTF-8: BCrypt no admite más (las tildes y la ñ ocupan 2).</li>
 *   <li>Sin el nombre de usuario y fuera de una lista de claves comunes.</li>
 * </ul>
 */
public final class PoliticaClaves {

	public static final int MINIMO = 10;
	public static final int MAXIMO = 64;
	public static final int MAXIMO_BYTES = 72;

	/** Claves de 10 o más caracteres que aparecen primero en cualquier ataque de diccionario. */
	private static final Set<String> COMUNES = Set.of(
			"1234567890", "0123456789", "12345678910", "123456789a", "a123456789", "1111111111", "0000000000",
			"qwertyuiop", "qwerty1234", "qwerty12345", "asdfghjkl1", "abcdefghij", "abc1234567", "1q2w3e4r5t",
			"password12", "password123", "password1234", "contraseña", "contrasena", "contraseña1",
			"contraseña123", "contrasena123", "administrador", "admin12345", "bienvenido", "bienvenido1",
			"bienvenido123", "iloveyou12", "teamo12345", "peru123456", "lima123456", "colegio123", "colegio2026",
			"virgenmaria", "virgenmaria1", "virgenmaria123", "colegiovirgenmaria", "cuentasclaras",
			"cuentasclaras1", "cuentasclaras123", "promotor123", "director123", "secretaria", "secretaria1");

	private PoliticaClaves() {
	}

	public static void validar(String clave, String nombreUsuario) {
		if (clave == null || clave.length() < MINIMO) {
			throw new ReglaNegocioException("Tu clave debe tener al menos " + MINIMO
					+ " caracteres. Una frase corta funciona bien, por ejemplo «mi gato duerme al sol».");
		}
		if (clave.length() > MAXIMO) {
			throw new ReglaNegocioException("Tu clave puede tener como máximo " + MAXIMO + " caracteres.");
		}
		if (clave.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
			throw new ReglaNegocioException("Tu clave es demasiado larga: acórtala un poco (las tildes y la ñ cuentan doble).");
		}
		String normalizada = clave.toLowerCase(Locale.ROOT);
		String usuario = nombreUsuario == null ? "" : nombreUsuario.trim().toLowerCase(Locale.ROOT);
		if (!usuario.isEmpty() && normalizada.contains(usuario)) {
			throw new ReglaNegocioException("Tu clave no puede contener tu nombre de usuario.");
		}
		if (COMUNES.contains(normalizada.replace(" ", ""))) {
			throw new ReglaNegocioException("Esa clave es muy común y fácil de adivinar. Elige otra.");
		}
	}
}
