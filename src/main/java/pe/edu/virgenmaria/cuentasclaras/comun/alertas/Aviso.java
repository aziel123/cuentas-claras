package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Identidad de una alerta para avisarla UNA sola vez al celular (sprint 6, tanda 2; hallazgo 3).
 *
 * @param tipo       el texto fijo del mensaje
 * @param referencia el registro que la origina (id del cierre, de la caja, de la solicitud más reciente…): con el tipo
 *                   forma la clave del mensaje. Solo {@code [A-Za-z0-9:-]}, hasta 40 caracteres.
 * @param dato       un monto o una hora para el mensaje («S/ 50.00», «19:00»), NUNCA un nombre; {@code null} si no hay
 * @param excluidos  nombres de usuario que no deben recibirla aunque tengan el rol (quien pidió la anulación y la cajera
 *                   del pago: decisión 71)
 */
public record Aviso(TipoAviso tipo, String referencia, String dato, Set<String> excluidos) {

	private static final Pattern REFERENCIA = Pattern.compile("^[A-Za-z0-9:-]{1,40}$");

	/** Montos, horas o fechas: dígitos, «S/», puntos, comas, dos puntos, barras, guiones y espacios. */
	private static final Pattern DATO = Pattern.compile("^[0-9S/.,: -]{1,24}$");

	public Aviso {
		Objects.requireNonNull(tipo, "tipo");
		if (referencia == null || !REFERENCIA.matcher(referencia).matches()) {
			throw new IllegalArgumentException("Referencia de aviso no válida: " + referencia);
		}
		if (dato != null && !DATO.matcher(dato).matches()) {
			throw new IllegalArgumentException("El dato de un aviso es solo un monto, una hora o una fecha");
		}
		excluidos = excluidos == null ? Set.of() : Set.copyOf(excluidos);
	}

	public Aviso(TipoAviso tipo, String referencia) {
		this(tipo, referencia, null, Set.of());
	}

	public Aviso(TipoAviso tipo, String referencia, String dato) {
		this(tipo, referencia, dato, Set.of());
	}
}
