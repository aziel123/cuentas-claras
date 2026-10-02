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
}
