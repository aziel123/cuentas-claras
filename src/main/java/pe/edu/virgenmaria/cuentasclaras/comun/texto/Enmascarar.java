package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import java.util.regex.Pattern;

/**
 * Datos personales enmascarados para la bitácora (Ley 29733): la bitácora es inmutable, así que nunca guarda
 * el documento, el celular ni el correo completos. Lo que queda alcanza para reconocer el cambio.
 */
public final class Enmascarar {

	private static final String OCULTO = "****";

	private Enmascarar() {
	}

	/** «DNI», «78451236» → «DNI ****1236». */
	public static String documento(String tipo, String numero) {
		String prefijo = tipo == null || tipo.isBlank() ? "" : tipo + " ";
		if (numero == null || numero.isBlank()) {
			return prefijo + "(sin número)";
		}
		return prefijo + OCULTO + ultimos(numero, 4);
	}

	/** «+51987654321» → «+51 *** *** 321». Otro país: «+** *** *** 321». */
	public static String telefono(String telefono) {
		if (telefono == null || telefono.isBlank()) {
			return null;
		}
		String digitos = telefono.replaceAll("\\D", "");
		String pais = telefono.startsWith("+51") ? "+51" : "+**";
		return pais + " *** *** " + ultimos(digitos, 3);
	}

	/** «rosa.huaman@gmail.com» → «r***@gmail.com». */
	public static String correo(String correo) {
		if (correo == null || correo.isBlank()) {
			return null;
		}
		int arroba = correo.indexOf('@');
		if (arroba <= 0) {
			return "***";
		}
		return correo.charAt(0) + "***" + correo.substring(arroba);
	}

	/** Duplicate entry de MySQL: el valor que chocó con una clave única (un DNI, un usuario, un correo). */
	private static final Pattern DUPLICADO = Pattern.compile("(?i)(duplicate entry ')[^']*(')");

	/**
	 * Tokens y secretos de 32 caracteres o más: en hexadecimal (hashes, llaves) o en base64url con dígitos, mayúsculas y
	 * minúsculas (enlaces de activación). Un nombre como {@code trg_partida_conciliacion_registro} no es un token.
	 */
	private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9_-])(?:[0-9A-Fa-f]{32,}|"
			+ "(?=[A-Za-z0-9_-]*[0-9])(?=[A-Za-z0-9_-]*[A-Z])(?=[A-Za-z0-9_-]*[a-z])[A-Za-z0-9_-]{32,})(?![A-Za-z0-9_-])");

	private static final Pattern CORREO = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	/** Celular peruano (9 dígitos que empiezan en 9), con o sin +51 y con espacios o guiones entre grupos. */
	private static final Pattern CELULAR = Pattern.compile(
			"(?<![0-9])(?:\\+?51[ -]?)?9[0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{3}(?![0-9])");

	/** RUC (11 dígitos) y DNI (8 dígitos), sueltos (no dentro de un número más largo). */
	private static final Pattern RUC = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])");

	private static final Pattern DNI = Pattern.compile("(?<![0-9])[0-9]{8}(?![0-9])");

	/**
	 * Sprint 7 (logs sin datos personales): oculta en un texto libre (mensaje de log o traza) los DNI, RUC, celulares,
	 * correos, tokens de 32 caracteres o más y el valor de un «Duplicate entry» de MySQL. Prefiere ocultar de más: un
	 * número de 8 dígitos que no es un DNI también se oculta.
	 */
	public static String enTexto(String texto) {
		if (texto == null || texto.isEmpty()) {
			return texto;
		}
		String limpio = DUPLICADO.matcher(texto).replaceAll("$1********$2");
		limpio = TOKEN.matcher(limpio).replaceAll("[oculto]");
		limpio = CORREO.matcher(limpio).replaceAll("***@***");
		limpio = CELULAR.matcher(limpio).replaceAll("*********");
		limpio = RUC.matcher(limpio).replaceAll("***********");
		return DNI.matcher(limpio).replaceAll("********");
	}

	private static String ultimos(String texto, int cantidad) {
		if (texto.length() <= cantidad) {
			// Muy corto: no se muestra nada para no revelarlo entero.
			return "";
		}
		return texto.substring(texto.length() - cantidad);
	}
}
