package pe.edu.virgenmaria.cuentasclaras.comun.fecha;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fechas del colegio. Nunca usa la configuración regional por defecto de la JVM: los meses salen en
 * español del Perú («setiembre»).
 */
public final class Calendario {

	/** Español del Perú: «setiembre» (con {@code es} sería «septiembre»). */
	public static final Locale PERU = Locale.forLanguageTag("es-PE");

	/** «d/M/uuuu» en modo estricto: el año con 4 dígitos y solo fechas que existen. */
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d/M/uuuu", PERU)
			.withResolverStyle(ResolverStyle.STRICT);

	private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/uuuu", PERU);

	private Calendario() {
	}

	/** 9 → «setiembre». */
	public static String nombreMes(int mes) {
		return Month.of(mes).getDisplayName(TextStyle.FULL, PERU);
	}

	/** Los 12 meses en español del Perú, de enero a diciembre («setiembre»). */
	public static List<String> nombresDeMeses() {
		List<String> meses = new ArrayList<>(12);
		for (int mes = 1; mes <= 12; mes++) {
			meses.add(nombreMes(mes));
		}
		return List.copyOf(meses);
	}

	public static LocalDate ultimoDiaDelMes(int anio, int mes) {
		return YearMonth.of(anio, mes).atEndOfMonth();
	}

	public static LocalDate primerDiaDelMes(LocalDate fecha) {
		return fecha.withDayOfMonth(1);
	}

	/** El último día de cada mes desde {@code primerMes}: (2027, 3, 10) → 31/03, 30/04, …, 31/12/2027. */
	public static List<LocalDate> vencimientosPorDefecto(int anio, int primerMes, int cantidad) {
		List<LocalDate> fechas = new ArrayList<>(cantidad);
		YearMonth mes = YearMonth.of(anio, primerMes);
		for (int k = 0; k < cantidad; k++) {
			fechas.add(mes.plusMonths(k).atEndOfMonth());
		}
		return fechas;
	}

	/** Edad cumplida al 31 de marzo del año escolar (regla del MINEDU para ubicar el grado). */
	public static int edadAl31DeMarzo(LocalDate nacimiento, int anio) {
		return Period.between(nacimiento, LocalDate.of(anio, 3, 31)).getYears();
	}

	/** «5/3/2015» o «05/03/2015». Rechaza años de dos dígitos y fechas que no existen (29/02/2015). */
	public static LocalDate parsearFecha(String texto) {
		if (texto == null || texto.isBlank()) {
			throw new ReglaNegocioException("Escribe la fecha como dd/mm/aaaa.");
		}
		try {
			return LocalDate.parse(texto.strip(), FECHA);
		}
		catch (DateTimeParseException e) {
			throw new ReglaNegocioException("La fecha «" + texto.strip() + "» no es válida: escríbela como dd/mm/aaaa "
					+ "(por ejemplo 05/03/2015) y revisa que exista.");
		}
	}

	/** 05/03/2015. */
	public static String formatear(LocalDate fecha) {
		return fecha == null ? "" : fecha.format(FECHA_CORTA);
	}
}
