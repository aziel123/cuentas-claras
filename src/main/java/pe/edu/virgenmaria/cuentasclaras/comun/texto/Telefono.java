package pe.edu.virgenmaria.cuentasclaras.comun.texto;

/** Celular para mostrar en pantalla: «+51987654321» → «+51 987 654 321». Otros números quedan como están. */
public final class Telefono {

	private Telefono() {
	}

	public static String formatear(String telefono) {
		if (telefono == null || !telefono.matches("^\\+519\\d{8}$")) {
			return telefono;
		}
		return "+51 " + telefono.substring(3, 6) + " " + telefono.substring(6, 9) + " " + telefono.substring(9);
	}

	/**
	 * Lo que alguien escribió como celular peruano, en el formato guardado: «987 654 321», «+51 987-654-321» o
	 * «51987654321» → «+51987654321». {@code null} si no es un celular peruano (9 dígitos que empiezan con 9).
	 */
	public static String normalizar(String escrito) {
		if (escrito == null) {
			return null;
		}
		String digitos = escrito.replaceAll("[\\s().\\-+]", "");
		if (!digitos.matches("\\d+")) {
			return null;
		}
		if (digitos.matches("^9\\d{8}$")) {
			return "+51" + digitos;
		}
		return digitos.matches("^519\\d{8}$") ? "+" + digitos : null;
	}
}
