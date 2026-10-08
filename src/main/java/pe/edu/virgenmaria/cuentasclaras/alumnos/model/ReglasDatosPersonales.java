package pe.edu.virgenmaria.cuentasclaras.alumnos.model;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Normalizador;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validación ÚNICA de los datos personales de alumnos y apoderados: la usan los formularios y (en la tanda 2) la
 * importación desde Excel. Pura: sin estado ni dependencias.
 * <p>
 * Cada método limpia el texto ({@link Normalizador}), lo valida y devuelve el valor tal como se guarda. Si no es
 * válido lanza {@link DatoInvalidoException} con el campo y un mensaje claro en español.
 */
public final class ReglasDatosPersonales {

	public static final int MAX_NOMBRE = 60;

	public static final int MAX_CORREO = 150;

	public static final int EDAD_MINIMA = 2;

	public static final int EDAD_MAXIMA = 20;

	public static final LocalDate NACIMIENTO_MINIMO = LocalDate.of(1990, 1, 1);

	/** Letras (con tildes y ñ), espacios, apóstrofo, punto y guion. Empieza con una letra. */
	private static final Pattern PATRON_NOMBRE = Pattern.compile("^\\p{L}[\\p{L}\\p{M} '’.\\-]*$");

	private static final Pattern PATRON_CORREO = Pattern.compile(
			"^[a-z0-9._%+\\-]+@[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?)*\\.[a-z]{2,}$");

	/** Celular peruano: 9 dígitos que empiezan con 9. */
	private static final Pattern CELULAR_PERU = Pattern.compile("^9\\d{8}$");

	/** Número internacional E.164 sin el +: de 8 a 15 dígitos, sin empezar con 0. */
	private static final Pattern E164 = Pattern.compile("^[1-9]\\d{7,14}$");

	/** Lo que una hoja de cálculo podría interpretar como fórmula. */
	private static final String INICIO_FORMULA = "=+-@";

	private static final int MAX_ECO = 30;

	private ReglasDatosPersonales() {
	}

	/**
	 * Documento sin espacios (tampoco NBSP ni invisibles) y en mayúsculas.
	 *
	 * @param campo nombre del campo del número de documento
	 */
	public static DocumentoIdentidad documento(TipoDocumento tipo, String numero, String campo) {
		if (tipo == null) {
			throw new DatoInvalidoException(campo, "Elige el tipo de documento.");
		}
		String limpio = Normalizador.sinEspacios(numero);
		if (limpio == null) {
			throw new DatoInvalidoException(campo, "Escribe el número de documento.");
		}
		exigirSinFormula(limpio, campo);
		limpio = limpio.toUpperCase(Locale.ROOT);
		if (!tipo.patron().matcher(limpio).matches()) {
			String mensaje = mayuscula(tipo.regla()) + "; escribiste «" + eco(limpio) + "».";
			if (tipo == TipoDocumento.DNI && limpio.chars().allMatch(Character::isDigit) && limpio.length() < 8) {
				mensaje += " Si el DNI empieza con 0, revisa que no se haya perdido el cero inicial "
						+ "(en Excel, formatea la columna como Texto).";
			}
			throw new DatoInvalidoException(campo, mensaje);
		}
		return new DocumentoIdentidad(tipo, limpio);
	}

	/** Igual que {@link #documento(TipoDocumento, String, String)} con el tipo escrito («DNI», «CE», «Pasaporte»). */
	public static DocumentoIdentidad documento(String tipo, String numero) {
		return documento(tipoDocumento(tipo, "tipoDocumento"), numero, "numeroDocumento");
	}

	public static TipoDocumento tipoDocumento(String texto, String campo) {
		String limpio = Normalizador.paraBusqueda(texto == null ? "" : texto);
		return Arrays.stream(TipoDocumento.values())
				.filter(t -> t.name().equals(limpio) || Normalizador.paraBusqueda(t.etiqueta()).equals(limpio))
				.findFirst()
				.orElseThrow(() -> new DatoInvalidoException(campo, "El tipo de documento debe ser DNI, CE o Pasaporte."));
	}

	/** Apellido o nombres obligatorios: de 1 a 60 caracteres, solo letras, espacios, apóstrofo, punto y guion. */
	public static String nombre(String valor, String campo) {
		String limpio = Normalizador.limpiar(valor);
		if (limpio == null) {
			throw new DatoInvalidoException(campo, "Este dato es obligatorio.");
		}
		return nombreValido(limpio, campo);
	}

	/** Como {@link #nombre} pero puede quedar vacío (apellido materno). */
	public static String nombreOpcional(String valor, String campo) {
		String limpio = Normalizador.limpiar(valor);
		return limpio == null ? null : nombreValido(limpio, campo);
	}

	/**
	 * Celular para WhatsApp en formato internacional: «987 654 321» → «+51987654321». Un fijo no sirve. Se acepta
	 * un número extranjero con + y código de país. {@code null} si está vacío.
	 */
	public static String telefonoWhatsapp(String valor, String campo) {
		String limpio = Normalizador.sinEspacios(valor);
		if (limpio == null) {
			return null;
		}
		if (limpio.charAt(0) == '=' || limpio.charAt(0) == '@') {
			throw formula(campo);
		}
		String numero = limpio.replaceAll("[\\-().]", "");
		if (!numero.matches("^\\+?\\d+$")) {
			throw new DatoInvalidoException(campo, "Escribe el celular solo con números, por ejemplo 987 654 321; "
					+ "escribiste «" + eco(limpio) + "».");
		}
		String digitos = numero.startsWith("+") ? numero.substring(1) : numero;
		boolean internacional = numero.startsWith("+");
		if (internacional && digitos.startsWith("51") || !internacional && digitos.length() == 11
				&& digitos.startsWith("51")) {
			String nacional = digitos.substring(2);
			if (CELULAR_PERU.matcher(nacional).matches()) {
				return "+51" + nacional;
			}
			throw fijo(campo, limpio);
		}
		if (internacional) {
			if (E164.matcher(digitos).matches()) {
				return "+" + digitos;
			}
			throw new DatoInvalidoException(campo, "El número internacional debe tener el + , el código de país y de 8 "
					+ "a 15 dígitos en total; escribiste «" + eco(limpio) + "».");
		}
		if (CELULAR_PERU.matcher(digitos).matches()) {
			return "+51" + digitos;
		}
		throw fijo(campo, limpio);
	}

	/** Correo en minúsculas, ASCII y hasta 150 caracteres. {@code null} si está vacío. */
	public static String correo(String valor, String campo) {
		String limpio = Normalizador.sinEspacios(valor);
		if (limpio == null) {
			return null;
		}
		exigirSinFormula(limpio, campo);
		String correo = limpio.toLowerCase(Locale.ROOT);
		if (correo.length() > MAX_CORREO || !PATRON_CORREO.matcher(correo).matches()) {
			throw new DatoInvalidoException(campo, "Revisa el correo: no parece válido (por ejemplo, rosa.huaman@gmail.com); "
					+ "escribiste «" + eco(limpio) + "».");
		}
		return correo;
	}

	/** El apoderado debe poder recibir avisos: WhatsApp o correo. */
	public static void exigirContacto(String telefonoWhatsapp, String correo, String campo) {
		if (telefonoWhatsapp == null && correo == null) {
			throw new DatoInvalidoException(campo, "Escribe el celular para WhatsApp o el correo del apoderado: "
					+ "lo necesitamos para avisarle de cada pago.");
		}
	}

	/**
	 * Fecha de nacimiento: no futura, desde 1990 y con una edad de 2 a 20 años al 31 de marzo del año escolar.
	 */
	public static LocalDate fechaNacimiento(LocalDate fecha, int anio, LocalDate hoy, String campo) {
		if (fecha == null) {
			throw new DatoInvalidoException(campo, "Escribe la fecha de nacimiento.");
		}
		if (fecha.isAfter(hoy)) {
			throw new DatoInvalidoException(campo, "La fecha de nacimiento no puede ser futura.");
		}
		if (fecha.isBefore(NACIMIENTO_MINIMO)) {
			throw new DatoInvalidoException(campo, "Revisa la fecha de nacimiento: debe ser de 1990 en adelante.");
		}
		int edad = Calendario.edadAl31DeMarzo(fecha, anio);
		if (edad < EDAD_MINIMA || edad > EDAD_MAXIMA) {
			throw new DatoInvalidoException(campo, "Con esa fecha tendría " + Math.max(edad, 0) + " años al 31/03/"
					+ anio + ". Revisa la fecha: un alumno tiene de " + EDAD_MINIMA + " a " + EDAD_MAXIMA + " años.");
		}
		return fecha;
	}

	/**
	 * Aviso (no bloquea) si la edad al 31 de marzo se aleja más de un año de la edad normal del grado.
	 */
	public static Optional<String> advertenciaEdad(LocalDate nacimiento, Grado grado, int anio) {
		if (nacimiento == null || grado == null) {
			return Optional.empty();
		}
		int edad = Calendario.edadAl31DeMarzo(nacimiento, anio);
		if (Math.abs(edad - grado.edadNormativa()) <= 1) {
			return Optional.empty();
		}
		return Optional.of("Tendrá " + edad + " años al 31/03/" + anio + " y lo usual en " + grado.etiqueta() + " son "
				+ grado.edadNormativa() + " años. Revisa el grado y la fecha de nacimiento.");
	}

	private static String nombreValido(String limpio, String campo) {
		exigirSinFormula(limpio, campo);
		if (limpio.length() > MAX_NOMBRE) {
			throw new DatoInvalidoException(campo, "Puede tener hasta " + MAX_NOMBRE + " caracteres.");
		}
		if (!PATRON_NOMBRE.matcher(limpio).matches()) {
			throw new DatoInvalidoException(campo, "Usa solo letras, espacios, apóstrofo o guion; escribiste «"
					+ eco(limpio) + "».");
		}
		return limpio;
	}

	private static void exigirSinFormula(String limpio, String campo) {
		if (INICIO_FORMULA.indexOf(limpio.charAt(0)) >= 0) {
			throw formula(campo);
		}
	}

	private static DatoInvalidoException formula(String campo) {
		return new DatoInvalidoException(campo, "No puede empezar con =, +, - ni @.");
	}

	private static DatoInvalidoException fijo(String campo, String escrito) {
		return new DatoInvalidoException(campo, "Escribe un celular de 9 dígitos que empiece con 9 (un teléfono fijo "
				+ "no recibe WhatsApp), o un número extranjero con + y el código de país; escribiste «" + eco(escrito)
				+ "».");
	}

	private static String eco(String texto) {
		return texto.length() <= MAX_ECO ? texto : texto.substring(0, MAX_ECO) + "…";
	}

	private static String mayuscula(String texto) {
		return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}
}
