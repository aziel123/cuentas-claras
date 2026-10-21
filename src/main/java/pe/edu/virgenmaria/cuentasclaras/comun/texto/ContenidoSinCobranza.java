package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import java.util.regex.Pattern;

/**
 * Sprint 9 (secciones 3.6, 3.13 y 7.3; INDECOPI y Ley 29571): un aviso de asistencia (y, en la tanda 3, de comunicados)
 * nunca habla de cobranza: montos, «S/», deudas, pensiones, cuotas, pagos, morosidad, cobros, vencimientos, Yape o Plin.
 * Es LA MISMA expresión que usa {@code trg_mensaje_nace} en MySQL sobre los parámetros del mensaje (una sola fuente:
 * {@code ContenidoSinCobranzaTest} la compara con la de {@code scripts/mysql/03-triggers.sql}). Con límites de palabra:
 * nombres como «Pagán» o «Vencesla» no coinciden.
 */
public final class ContenidoSinCobranza {

	/** La expresión, tal cual va (con las barras sin duplicar) en el REGEXP_LIKE de trg_mensaje_nace. */
	public static final String EXPRESION = "S/|US[$]|[0-9][.,][0-9]{2}([^0-9]|$)|\\bdeud|\\bpensi[oó]n|\\bcuotas?\\b"
			+ "|\\bpag(o|os|ar|ue|ues)\\b|\\bmoros|\\bcobr|\\bvencid|\\byape\\b|\\bplin\\b";

	private static final Pattern PATRON = Pattern.compile(EXPRESION,
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

	private ContenidoSinCobranza() {
	}

	/** Si el texto habla de cobranza (y entonces el aviso académico no debe salir con él). */
	public static boolean hablaDeCobranza(String texto) {
		return texto != null && PATRON.matcher(texto).find();
	}
}
