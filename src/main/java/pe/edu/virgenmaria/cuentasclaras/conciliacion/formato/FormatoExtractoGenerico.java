package pe.edu.virgenmaria.cuentasclaras.conciliacion.formato;

import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Formato genérico del extracto (sección 10.3 del diseño del sprint 4), común al CSV y al XLSX. Antes de la cabecera
 * puede venir la línea {@code cuenta;<número>}; la cabecera lleva {@code fecha}, {@code descripcion},
 * {@code numero_operacion} (opcional), {@code referencia} (opcional), {@code cargo}, {@code abono}, {@code saldo} y,
 * si no vino antes, {@code cuenta}, en cualquier orden.
 * <ul>
 *   <li>Fecha {@code AAAA-MM-DD} o {@code DD/MM/AAAA}; las filas en orden de fecha (como las da el banco).</li>
 *   <li>Cada fila es un cargo O un abono, con punto decimal y hasta 2 decimales; una coma es un error.</li>
 *   <li>El SALDO es obligatorio y debe cuadrar fila por fila: saldo = saldo anterior + abono − cargo. Un saldo que no
 *       cuadra es un archivo incompleto o alterado.</li>
 *   <li>La glosa se guarda limpia y con hasta 200 caracteres; si empieza como una fórmula de Excel, se le antepone un
 *       apóstrofo.</li>
 * </ul>
 * Puro: sin base de datos.
 */
public abstract class FormatoExtractoGenerico implements AdaptadorExtracto {

	static final String CUENTA = "cuenta";

	static final String FECHA = "fecha";

	static final String DESCRIPCION = "descripcion";

	static final String NUMERO_OPERACION = "numero_operacion";

	static final String REFERENCIA = "referencia";

	static final String CARGO = "cargo";

	static final String ABONO = "abono";

	static final String SALDO = "saldo";

	static final List<String> OBLIGATORIAS = List.of(FECHA, DESCRIPCION, CARGO, ABONO, SALDO);

	static final List<String> CONOCIDAS = List.of(CUENTA, FECHA, DESCRIPCION, NUMERO_OPERACION, REFERENCIA, CARGO, ABONO,
			SALDO);

	static final int MAXIMO_ERRORES = 50;

	private static final Pattern MONTO = Pattern.compile("^\\d{1,10}(\\.\\d{1,2})?$");

	private static final Pattern SALDO_VALIDO = Pattern.compile("^-?\\d{1,12}(\\.\\d{1,2})?$");

	private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd")
			.withResolverStyle(ResolverStyle.STRICT);

	private static final DateTimeFormatter PERUANA = DateTimeFormatter.ofPattern("dd/MM/uuuu")
			.withResolverStyle(ResolverStyle.STRICT);

	/** Una fila del archivo como texto: su línea (o fila de Excel) y sus celdas. */
	protected record FilaCruda(int linea, List<String> celdas) {

		String celda(int columna) {
			return columna >= 0 && columna < celdas.size() && celdas.get(columna) != null ? celdas.get(columna).strip()
					: "";
		}
	}

	/** Convierte las filas (preámbulo opcional, cabecera y movimientos) en movimientos validados. */
	protected static LecturaExtracto convertir(String formato, List<FilaCruda> filas) {
		if (filas.isEmpty()) {
			throw new ArchivoNoValidoException("El archivo está vacío.");
		}
		String cuenta = null;
		int inicio = 0;
		while (inicio < filas.size() && !esCabecera(filas.get(inicio))) {
			FilaCruda fila = filas.get(inicio);
			if (CUENTA.equals(normalizar(fila.celda(0))) && !fila.celda(1).isEmpty()) {
				cuenta = fila.celda(1);
			}
			inicio++;
			if (inicio > 10) {
				break;
			}
		}
		if (inicio >= filas.size() || !esCabecera(filas.get(inicio))) {
			throw new ArchivoNoValidoException("No se encontró la cabecera del extracto. Debe tener las columnas "
					+ String.join(";", OBLIGATORIAS) + " (y numero_operacion, referencia y cuenta si las hay).");
		}
		Map<String, Integer> columnas = columnas(filas.get(inicio));
		if (cuenta == null && !columnas.containsKey(CUENTA)) {
			throw new ArchivoNoValidoException("El extracto no dice de qué cuenta es: agrega la línea «cuenta;<número>» "
					+ "antes de la cabecera o la columna cuenta.");
		}
		List<FilaExtracto> movimientos = new ArrayList<>();
		List<ErrorExtracto> errores = new ArrayList<>();
		FilaExtracto anterior = null;
		for (FilaCruda fila : filas.subList(inicio + 1, filas.size())) {
			List<String> problemas = new ArrayList<>();
			if (columnas.containsKey(CUENTA)) {
				String deLaFila = fila.celda(columnas.get(CUENTA));
				if (cuenta == null) {
					cuenta = deLaFila;
				}
				else if (!deLaFila.isEmpty() && !soloDigitos(deLaFila).equals(soloDigitos(cuenta))) {
					problemas.add("la fila es de otra cuenta (" + recortar(deLaFila) + "): un extracto es de una sola cuenta");
				}
			}
			FilaExtracto leida = leerFila(fila, columnas, problemas);
			if (leida != null && anterior != null) {
				if (leida.fecha().isBefore(anterior.fecha())) {
					problemas.add("la fecha es anterior a la de la fila previa: las filas van en el orden del banco");
				}
				else if (leida.saldo().compareTo(anterior.saldo().add(leida.efecto())) != 0) {
					problemas.add("el saldo no cuadra con el de la fila anterior (" + anterior.saldo().toPlainString()
							+ (leida.tipo() == TipoMovimiento.ABONO ? " + " : " − ") + leida.monto().toPlainString()
							+ " no es " + leida.saldo().toPlainString() + "): el archivo está incompleto o alterado");
				}
			}
			if (!problemas.isEmpty()) {
				agregar(errores, new ErrorExtracto(fila.linea(), String.join("; ", problemas)));
				continue;
			}
			movimientos.add(leida);
			anterior = leida;
		}
		if (movimientos.isEmpty() && errores.isEmpty()) {
			throw new ArchivoNoValidoException("El extracto no tiene movimientos: solo la cabecera.");
		}
		return new LecturaExtracto(formato, cuenta == null ? "" : cuenta.strip(), movimientos, errores);
	}

	private static boolean esCabecera(FilaCruda fila) {
		List<String> nombres = fila.celdas().stream().map(FormatoExtractoGenerico::normalizar).toList();
		return nombres.contains(FECHA) && nombres.contains(SALDO);
	}

	private static Map<String, Integer> columnas(FilaCruda cabecera) {
		Map<String, Integer> columnas = new HashMap<>();
		for (int i = 0; i < cabecera.celdas().size(); i++) {
			String nombre = normalizar(cabecera.celda(i));
			if (!CONOCIDAS.contains(nombre)) {
				continue;
			}
			if (columnas.put(nombre, i) != null) {
				throw new ArchivoNoValidoException("La columna «" + nombre + "» está dos veces en la cabecera.");
			}
		}
		List<String> faltan = OBLIGATORIAS.stream().filter(c -> !columnas.containsKey(c)).toList();
		if (!faltan.isEmpty()) {
			throw new ArchivoNoValidoException("Faltan columnas en la cabecera del extracto: " + String.join(", ", faltan)
					+ ". El saldo es obligatorio: sin él no se puede comprobar que el extracto continúa al anterior.");
		}
		return columnas;
	}

	private static FilaExtracto leerFila(FilaCruda fila, Map<String, Integer> columnas, List<String> problemas) {
		LocalDate fecha = fecha(fila.celda(columnas.get(FECHA)), problemas);
		String descripcion = Normalizador.limpiar(fila.celda(columnas.get(DESCRIPCION)));
		if (descripcion == null) {
			problemas.add("falta la descripción (glosa) del banco");
		}
		else {
			descripcion = recortar(descripcion, 200);
			if (TextoSeguro.pareceFormula(descripcion)) {
				descripcion = recortar("'" + descripcion, 200);
			}
		}
		String operacion = null;
		String textoOperacion = columnas.containsKey(NUMERO_OPERACION) ? fila.celda(columnas.get(NUMERO_OPERACION)) : "";
		if (!textoOperacion.isEmpty()) {
			try {
				operacion = NumeroOperacion.normalizar(textoOperacion);
			}
			catch (ReglaNegocioException e) {
				// Un número del banco que no se puede llevar a la forma canónica no sirve para emparejar exacto; la glosa
				// lo conserva.
				operacion = null;
			}
		}
		String referencia = columnas.containsKey(REFERENCIA)
				? Normalizador.limpiar(fila.celda(columnas.get(REFERENCIA))) : null;
		if (referencia != null) {
			referencia = recortar(referencia, 60);
			if (TextoSeguro.pareceFormula(referencia)) {
				referencia = recortar("'" + referencia, 60);
			}
		}
		String cargo = fila.celda(columnas.get(CARGO));
		String abono = fila.celda(columnas.get(ABONO));
		TipoMovimiento tipo = null;
		BigDecimal monto = null;
		if (vacio(cargo) == vacio(abono)) {
			problemas.add("cada fila es un cargo O un abono (escribe solo uno de los dos)");
		}
		else {
			tipo = vacio(cargo) ? TipoMovimiento.ABONO : TipoMovimiento.CARGO;
			monto = monto(vacio(cargo) ? abono : cargo, problemas);
		}
		BigDecimal saldo = null;
		String textoSaldo = fila.celda(columnas.get(SALDO));
		if (!SALDO_VALIDO.matcher(textoSaldo).matches()) {
			problemas.add("el saldo es obligatorio, con punto decimal y sin comas (dice «" + recortar(textoSaldo) + "»)");
		}
		else {
			saldo = new BigDecimal(textoSaldo).setScale(Dinero.ESCALA);
		}
		if (!problemas.isEmpty() || fecha == null || monto == null) {
			return null;
		}
		return new FilaExtracto(fila.linea(), fecha, descripcion, operacion, referencia, tipo, monto, saldo);
	}

	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank() || texto.strip().matches("0+(\\.0+)?");
	}

	private static LocalDate fecha(String texto, List<String> problemas) {
		for (DateTimeFormatter formato : List.of(ISO, PERUANA)) {
			try {
				return LocalDate.parse(texto, formato);
			}
			catch (DateTimeParseException e) {
				// se prueba el otro formato
			}
		}
		problemas.add("la fecha debe ser AAAA-MM-DD o DD/MM/AAAA (dice «" + recortar(texto) + "»)");
		return null;
	}

	private static BigDecimal monto(String texto, List<String> problemas) {
		if (!MONTO.matcher(texto).matches()) {
			problemas.add("el monto debe llevar punto decimal y hasta 2 decimales, sin comas ni símbolos (dice «"
					+ recortar(texto) + "»)");
			return null;
		}
		BigDecimal monto = new BigDecimal(texto).setScale(Dinero.ESCALA);
		if (monto.signum() <= 0) {
			problemas.add("el monto debe ser mayor que cero");
			return null;
		}
		return monto;
	}

	private static void agregar(List<ErrorExtracto> errores, ErrorExtracto error) {
		if (errores.size() < MAXIMO_ERRORES) {
			errores.add(error);
		}
	}

	static String soloDigitos(String texto) {
		return texto == null ? "" : texto.replaceAll("\\D", "");
	}

	/** Minúsculas, sin tildes ni espacios a los lados ({@code "Descripción "} → {@code descripcion}). */
	static String normalizar(String texto) {
		String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sinTildes.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	private static String recortar(String texto) {
		return texto.length() <= 20 ? texto : texto.substring(0, 20) + "…";
	}

	private static String recortar(String texto, int maximo) {
		return texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}
}
