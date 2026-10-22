package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Sprint 9 (secciones 3.6, 3.13 y 7.3; INDECOPI y Ley 29571): un aviso de asistencia o de comunicados, y el título y el
 * texto de un comunicado, nunca hablan de cobranza: montos, «S/», deudas, pensiones, cuotas, pagos, morosidad, cobros,
 * vencimientos, Yape o Plin. Es LA MISMA expresión que usan {@code trg_mensaje_nace} (sobre los parámetros, sin el nombre
 * del colegio: B6) y {@code trg_comunicado_nace} en MySQL (una sola fuente: {@code ContenidoSinCobranzaTest} la compara
 * con las de {@code scripts/mysql/03-triggers.sql}). Con límites de palabra: nombres como «Pagán» o «Vencesla» no
 * coinciden.
 * <p>
 * Tanda 3 (B7): «S/» solo cuenta delante de una cifra («padres/madres» pasa) y una cifra con dos decimales no cuenta si es
 * una hora («7.45 a.m.», «8.30 h»).
 */
public final class ContenidoSinCobranza {

	/** «S/» delante de una cifra o de un número en letras (QA-S9-5: «S/ trescientos»; «s/n» pasa). */
	private static final String SOLES = "(^|[^a-z])s/\\.?\\s*([0-9]|(un|uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieci[a-zéá]*|veinte|veinti[a-zéá]*|treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa|cien|ciento|doscientos|trescientos|cuatrocientos|quinientos|seiscientos|setecientos|ochocientos|novecientos|mil)\\b)";

	private static final String DOLARES = "US[$]";

	private static final String DECIMALES = "[0-9][.,][0-9]{2}(?![0-9]|\\s*([ap]\\.?\\s?m|h)\\b)";

	private static final String DEUDA = "\\bdeud";

	private static final String PENSION = "\\bpensi[oó]n|\\bcuotas?\\b";

	private static final String PAGO = "\\bpag(o|os|ar|ue|ues)\\b";

	private static final String MOROSIDAD = "\\bmoros";

	private static final String COBRO = "\\bcobr";

	private static final String VENCIDO = "\\bvencid";

	private static final String MEDIOS = "\\byape\\b|\\bplin\\b";

	/** Correcciones del sprint 9 (INDECOPI). */
	private static final String MENSUALIDAD = "\\bmensualidad";

	/** Correcciones del sprint 9 (INDECOPI). */
	private static final String AL_DIA = "\\bal\\s+d[ií]a\\s+con\\b";

	/** Correcciones del sprint 9 (INDECOPI). */
	private static final String OBLIGACIONES = "\\b(obligaciones|compromisos)\\s+econ[oó]mic";

	/** Correcciones del sprint 9 (INDECOPI). */
	private static final String SALDO = "\\bsaldos?\\b";

	/** Correcciones del sprint 9 (INDECOPI). */
	private static final String ABONO = "\\babon(o|os|ó|ar|e|en|ad[oa]s?|ando)\\b";

	/** Correcciones del sprint 9 (INDECOPI). */
	private static final String REGULARIZAR = "\\bregulariz";

	/** Correcciones del sprint 9 (INDECOPI). */
	private static final String CANCELAR = "\\bcancel(ar|en)\\s+(la|su)\\b";

	/** Correcciones del sprint 9 (INDECOPI). */
	private static final String EXAMEN = "\\bno\\s+podr[aá]n?\\s+(rendir|dar)\\s+(el\\s+)?ex[aá]men";

	/** La expresión, tal cual va (con las barras sin duplicar) en los REGEXP_LIKE de trg_mensaje_nace y trg_comunicado_nace. */
	public static final String EXPRESION = String.join("|", SOLES, DOLARES, DECIMALES, DEUDA, PENSION, PAGO, MOROSIDAD,
			COBRO, VENCIDO, MEDIOS, MENSUALIDAD, AL_DIA, OBLIGACIONES, SALDO, ABONO, REGULARIZAR, CANCELAR, EXAMEN);

	private static final int BANDERAS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

	private static final Pattern PATRON = Pattern.compile(EXPRESION, BANDERAS);

	/** Cada parte con el motivo que ve quien escribe (en el orden en que se revisan). */
	private static final List<Regla> REGLAS = List.of(new Regla(SOLES, "menciona un monto en soles"),
			new Regla(DOLARES, "menciona un monto en dólares"), new Regla(DECIMALES, "menciona un monto con decimales"),
			new Regla(DEUDA, "menciona una deuda"), new Regla(PENSION, "menciona pensiones o cuotas"),
			new Regla(PAGO, "menciona pagos"), new Regla(MOROSIDAD, "menciona morosidad"),
			new Regla(COBRO, "menciona cobros o cobranza"), new Regla(VENCIDO, "menciona un vencimiento"),
			new Regla(MEDIOS, "menciona Yape o Plin"),
			new Regla(MENSUALIDAD, "menciona la mensualidad"),
			new Regla(AL_DIA, "pide estar al día con algo"),
			new Regla(OBLIGACIONES, "menciona obligaciones económicas"),
			new Regla(SALDO, "menciona un saldo"),
			new Regla(ABONO, "menciona un abono"),
			new Regla(REGULARIZAR, "pide regularizar algo"),
			new Regla(CANCELAR, "pide cancelar algo"),
			new Regla(EXAMEN, "condiciona un examen"));

	private ContenidoSinCobranza() {
	}

	/** Si el texto habla de cobranza (y entonces el aviso académico o el comunicado no debe salir con él). */
	public static boolean hablaDeCobranza(String texto) {
		return texto != null && PATRON.matcher(texto).find();
	}

	/**
	 * Sprint 9, tanda 3 (B6): si el nombre de un colegio calza con la regla, los avisos académicos siguen saliendo (la base
	 * la aplica solo a lo que va después del nombre), pero conviene saberlo: el aviso para quien da de alta o corrige la
	 * ficha. Vacío si el nombre no calza.
	 */
	public static Optional<String> advertenciaNombreColegio(String nombre) {
		return motivo(nombre).map(m -> "El nombre del colegio " + m + " para la regla que protege los avisos de asistencia "
				+ "y de comunicados. Los avisos salen igual (el nombre no se revisa), pero revisa que esté bien escrito.");
	}

	/** El motivo claro por el que el texto habla de cobranza («menciona un monto en soles»), o vacío si no habla. */
	public static Optional<String> motivo(String texto) {
		if (!hablaDeCobranza(texto)) {
			return Optional.empty();
		}
		return REGLAS.stream().filter(r -> r.patron().matcher(texto).find()).map(Regla::motivo).findFirst();
	}

	private record Regla(Pattern patron, String motivo) {

		Regla(String expresion, String motivo) {
			this(Pattern.compile(expresion, BANDERAS), motivo);
		}
	}
}
