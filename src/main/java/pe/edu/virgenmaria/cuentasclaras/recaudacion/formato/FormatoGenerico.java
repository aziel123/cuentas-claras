package pe.edu.virgenmaria.cuentasclaras.recaudacion.formato;

import pe.edu.virgenmaria.cuentasclaras.caja.model.NumeroOperacion;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.excel.ArchivoNoValidoException;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.BancoRecaudacion;

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
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formato genérico de recaudación (sección 10.3 del diseño), común al CSV y al XLSX: una cabecera con las columnas
 * {@code fecha_pago}, {@code codigo_alumno}, {@code referencia_deuda} (opcional), {@code monto}, {@code moneda},
 * {@code numero_operacion} y {@code canal} (opcional, no se guarda), en cualquier orden, y un pie opcional
 * {@code TOTAL;<monto>;<cantidad>} que debe coincidir con las líneas.
 * <ul>
 *   <li>Fecha {@code AAAA-MM-DD} o {@code DD/MM/AAAA}, nunca posterior a hoy.</li>
 *   <li>Monto con punto decimal y hasta 2 decimales ({@code 1250.00}); una coma es un error (no se adivina si es de
 *       miles o decimal).</li>
 *   <li>Moneda PEN o USD (un pago en dólares queda en excepción: nunca se convierte).</li>
 *   <li>Número de operación en su forma canónica ({@link NumeroOperacion}).</li>
 * </ul>
 * Puro: sin base de datos.
 */
public abstract class FormatoGenerico implements AdaptadorRecaudacion {

	static final String FECHA_PAGO = "fecha_pago";

	static final String CODIGO_ALUMNO = "codigo_alumno";

	static final String REFERENCIA_DEUDA = "referencia_deuda";

	static final String MONTO = "monto";

	static final String MONEDA = "moneda";

	static final String NUMERO_OPERACION = "numero_operacion";

	static final String CANAL = "canal";

	static final List<String> OBLIGATORIAS = List.of(FECHA_PAGO, CODIGO_ALUMNO, MONTO, MONEDA, NUMERO_OPERACION);

	static final List<String> CONOCIDAS = List.of(FECHA_PAGO, CODIGO_ALUMNO, REFERENCIA_DEUDA, MONTO, MONEDA,
			NUMERO_OPERACION, CANAL);

	/** Errores que se muestran como máximo (los demás se cuentan). */
	static final int MAXIMO_ERRORES = 50;

	private static final Pattern MONTO_VALIDO = Pattern.compile("^\\d{1,10}(\\.\\d{1,2})?$");

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

	@Override
	public BancoRecaudacion banco() {
		return BancoRecaudacion.GENERICO;
	}

	/**
	 * Convierte las filas (la primera es la cabecera) en pagos validados.
	 *
	 * @param formato nombre del formato que se guarda en el lote
	 */
	protected static LecturaRecaudacion convertir(String formato, List<FilaCruda> filas, LocalDate hoy) {
		if (filas.isEmpty()) {
			throw new ArchivoNoValidoException("El archivo está vacío.");
		}
		Map<String, Integer> columnas = columnas(filas.getFirst());
		List<FilaRecaudacion> pagos = new ArrayList<>();
		List<ErrorFila> errores = new ArrayList<>();
		BigDecimal totalDeclarado = null;
		Integer cantidadDeclarada = null;
		Integer lineaDelPie = null;
		for (FilaCruda fila : filas.subList(1, filas.size())) {
			if (lineaDelPie != null) {
				agregar(errores, new ErrorFila(fila.linea(), "Hay datos después del pie «TOTAL» (línea " + lineaDelPie
						+ "): el pie va al final."));
				continue;
			}
			if ("TOTAL".equalsIgnoreCase(fila.celda(0))) {
				lineaDelPie = fila.linea();
				totalDeclarado = montoDelPie(fila, errores);
				cantidadDeclarada = cantidadDelPie(fila, errores);
				continue;
			}
			leerFila(fila, columnas, pagos.size() + 1, hoy, errores).ifPresent(pagos::add);
		}
		if (pagos.isEmpty() && errores.isEmpty()) {
			throw new ArchivoNoValidoException("El archivo no tiene pagos: solo la cabecera.");
		}
		BigDecimal suma = Dinero.sumar(pagos.stream().map(FilaRecaudacion::monto).toList());
		if (errores.isEmpty() && totalDeclarado != null && totalDeclarado.compareTo(suma) != 0) {
			agregar(errores, new ErrorFila(lineaDelPie, "El total del pie (" + Dinero.formatear(totalDeclarado)
					+ ") no es la suma de las líneas (" + Dinero.formatear(suma) + "). El archivo está incompleto o "
					+ "alterado: descárgalo otra vez del portal del banco."));
		}
		if (errores.isEmpty() && cantidadDeclarada != null && cantidadDeclarada != pagos.size()) {
			agregar(errores, new ErrorFila(lineaDelPie, "El pie dice " + cantidadDeclarada + " pagos y el archivo tiene "
					+ pagos.size() + ". El archivo está incompleto o alterado: descárgalo otra vez del portal del banco."));
		}
		LocalDate fechaProceso = pagos.stream().map(FilaRecaudacion::fechaPago).max(LocalDate::compareTo).orElse(hoy);
		return new LecturaRecaudacion(formato, fechaProceso, totalDeclarado, cantidadDeclarada, List.copyOf(pagos),
				List.copyOf(errores));
	}

	private static Map<String, Integer> columnas(FilaCruda cabecera) {
		Map<String, Integer> columnas = new HashMap<>();
		for (int i = 0; i < cabecera.celdas().size(); i++) {
			String nombre = normalizar(cabecera.celda(i));
			if (nombre.isEmpty()) {
				continue;
			}
			if (!CONOCIDAS.contains(nombre)) {
				continue;
			}
			if (columnas.put(nombre, i) != null) {
				throw new ArchivoNoValidoException("La columna «" + nombre + "» está dos veces en la cabecera.");
			}
		}
		List<String> faltan = OBLIGATORIAS.stream().filter(c -> !columnas.containsKey(c)).toList();
		if (!faltan.isEmpty()) {
			throw new ArchivoNoValidoException("Faltan columnas en la primera fila: " + String.join(", ", faltan)
					+ ". La cabecera del formato genérico es: " + String.join(";", CONOCIDAS) + ".");
		}
		return columnas;
	}

	private static Optional<FilaRecaudacion> leerFila(FilaCruda fila, Map<String, Integer> columnas, int numero,
			LocalDate hoy, List<ErrorFila> errores) {
		List<String> problemas = new ArrayList<>();
		LocalDate fecha = fecha(fila.celda(columnas.get(FECHA_PAGO)), hoy, problemas);
		String codigo = codigo(fila.celda(columnas.get(CODIGO_ALUMNO)), "código de alumno", true, problemas);
		String referencia = columnas.containsKey(REFERENCIA_DEUDA)
				? codigo(fila.celda(columnas.get(REFERENCIA_DEUDA)), "referencia de deuda", false, problemas) : null;
		BigDecimal monto = monto(fila.celda(columnas.get(MONTO)), problemas);
		String moneda = fila.celda(columnas.get(MONEDA)).toUpperCase(Locale.ROOT);
		if (!moneda.equals("PEN") && !moneda.equals("USD")) {
			problemas.add("la moneda debe ser PEN o USD (dice «" + recortar(moneda) + "»)");
		}
		String operacion = null;
		try {
			operacion = NumeroOperacion.normalizar(fila.celda(columnas.get(NUMERO_OPERACION)));
		}
		catch (ReglaNegocioException e) {
			problemas.add("número de operación: " + e.getMessage().toLowerCase(Locale.ROOT));
		}
		if (!problemas.isEmpty()) {
			agregar(errores, new ErrorFila(fila.linea(), String.join("; ", problemas)));
			return Optional.empty();
		}
		return Optional.of(new FilaRecaudacion(numero, fila.linea(), fecha, codigo, referencia, monto, moneda, operacion));
	}

	private static LocalDate fecha(String texto, LocalDate hoy, List<String> problemas) {
		LocalDate fecha = null;
		for (DateTimeFormatter formato : List.of(ISO, PERUANA)) {
			try {
				fecha = LocalDate.parse(texto, formato);
				break;
			}
			catch (DateTimeParseException e) {
				// se prueba el otro formato
			}
		}
		if (fecha == null) {
			problemas.add("la fecha de pago debe ser AAAA-MM-DD o DD/MM/AAAA (dice «" + recortar(texto) + "»)");
		}
		else if (fecha.isAfter(hoy)) {
			problemas.add("la fecha de pago (" + texto + ") es posterior a hoy");
			fecha = null;
		}
		return fecha;
	}

	private static String codigo(String texto, String nombre, boolean obligatorio, List<String> problemas) {
		String limpio = texto.replaceAll("[\\s-]", "");
		if (limpio.isEmpty()) {
			if (obligatorio) {
				problemas.add("falta el " + nombre);
			}
			return null;
		}
		if (limpio.length() > 20) {
			problemas.add("el " + nombre + " es demasiado largo");
			return null;
		}
		return limpio;
	}

	private static BigDecimal monto(String texto, List<String> problemas) {
		if (!MONTO_VALIDO.matcher(texto).matches()) {
			problemas.add("el monto debe llevar punto decimal y hasta 2 decimales, sin comas ni símbolos (por ejemplo "
					+ "1250.00; dice «" + recortar(texto) + "»)");
			return null;
		}
		BigDecimal monto = new BigDecimal(texto).setScale(Dinero.ESCALA);
		if (monto.signum() <= 0 || monto.compareTo(Dinero.MAXIMO) > 0) {
			problemas.add("el monto debe estar entre S/ 0.01 y " + Dinero.formatear(Dinero.MAXIMO));
			return null;
		}
		return monto;
	}

	private static BigDecimal montoDelPie(FilaCruda pie, List<ErrorFila> errores) {
		List<String> problemas = new ArrayList<>();
		BigDecimal total = null;
		String texto = pie.celda(1);
		if (!MONTO_VALIDO.matcher(texto).matches()) {
			problemas.add("el total del pie debe llevar punto decimal (por ejemplo 12500.00)");
		}
		else {
			total = new BigDecimal(texto).setScale(Dinero.ESCALA);
		}
		if (!problemas.isEmpty()) {
			agregar(errores, new ErrorFila(pie.linea(), String.join("; ", problemas)));
		}
		return total;
	}

	private static Integer cantidadDelPie(FilaCruda pie, List<ErrorFila> errores) {
		String texto = pie.celda(2);
		if (texto.isEmpty()) {
			return null;
		}
		if (!texto.matches("\\d{1,6}")) {
			agregar(errores, new ErrorFila(pie.linea(), "la cantidad del pie debe ser un número entero"));
			return null;
		}
		return Integer.valueOf(texto);
	}

	private static void agregar(List<ErrorFila> errores, ErrorFila error) {
		if (errores.size() < MAXIMO_ERRORES) {
			errores.add(error);
		}
	}

	/** Minúsculas, sin tildes ni espacios a los lados ({@code "Código_Alumno "} → {@code codigo_alumno}). */
	static String normalizar(String texto) {
		String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sinTildes.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	private static String recortar(String texto) {
		return texto.length() <= 20 ? texto : texto.substring(0, 20) + "…";
	}
}
