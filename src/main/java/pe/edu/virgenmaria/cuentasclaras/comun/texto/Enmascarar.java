package pe.edu.virgenmaria.cuentasclaras.comun.texto;

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

	private static String ultimos(String texto, int cantidad) {
		if (texto.length() <= cantidad) {
			// Muy corto: no se muestra nada para no revelarlo entero.
			return "";
		}
		return texto.substring(texto.length() - cantidad);
	}
}
