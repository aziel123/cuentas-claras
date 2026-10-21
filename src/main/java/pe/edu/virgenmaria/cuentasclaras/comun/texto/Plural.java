package pe.edu.virgenmaria.cuentasclaras.comun.texto;

/**
 * Rediseño de la interfaz (clarify): plurales reales en lo que se muestra («1 caja abierta», «3 pagos») en vez de
 * «abierta(s)».
 */
public final class Plural {

	private Plural() {
	}

	/** {@code 1 pago} o {@code 3 pagos}: la cantidad y la forma que corresponde. */
	public static String de(long cantidad, String singular, String plural) {
		return cantidad + " " + segun(cantidad, singular, plural);
	}

	/** Solo la forma que corresponde, sin la cantidad: para el verbo («sigue» o «siguen», «lleva» o «llevan»). */
	public static String segun(long cantidad, String singular, String plural) {
		return cantidad == 1 ? singular : plural;
	}
}
