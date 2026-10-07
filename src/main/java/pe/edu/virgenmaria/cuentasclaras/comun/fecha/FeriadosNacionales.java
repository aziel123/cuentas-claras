package pe.edu.virgenmaria.cuentasclaras.comun.fecha;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los 16 feriados nacionales del Perú (sprint 5, tanda 3; decisión 59). Están en el código y NADIE los edita: un feriado
 * nuevo por ley requiere un despliegue (mientras tanto, Promotoría o Dirección lo registran como día no laborable extra).
 * El Jueves y el Viernes Santo salen de la fecha de Pascua (algoritmo de Meeus/Jones/Butcher, calendario gregoriano).
 */
public final class FeriadosNacionales {

	/** Los 14 de fecha fija, con su nombre. */
	private static final Map<MonthDay, String> FIJOS = fijos();

	private static final Map<Integer, Map<LocalDate, String>> POR_ANIO = new ConcurrentHashMap<>();

	private FeriadosNacionales() {
	}

	private static Map<MonthDay, String> fijos() {
		Map<MonthDay, String> m = new LinkedHashMap<>();
		m.put(MonthDay.of(1, 1), "Año Nuevo");
		m.put(MonthDay.of(5, 1), "Día del Trabajo");
		m.put(MonthDay.of(6, 7), "Batalla de Arica y Día de la Bandera");
		m.put(MonthDay.of(6, 29), "San Pedro y San Pablo");
		m.put(MonthDay.of(7, 23), "Día de la Fuerza Aérea del Perú");
		m.put(MonthDay.of(7, 28), "Fiestas Patrias");
		m.put(MonthDay.of(7, 29), "Fiestas Patrias");
		m.put(MonthDay.of(8, 6), "Batalla de Junín");
		m.put(MonthDay.of(8, 30), "Santa Rosa de Lima");
		m.put(MonthDay.of(10, 8), "Combate de Angamos");
		m.put(MonthDay.of(11, 1), "Día de Todos los Santos");
		m.put(MonthDay.of(12, 8), "Inmaculada Concepción");
		m.put(MonthDay.of(12, 9), "Batalla de Ayacucho");
		m.put(MonthDay.of(12, 25), "Navidad");
		return Map.copyOf(m);
	}

	/** Los 16 feriados del año, en orden, con su nombre. */
	public static Map<LocalDate, String> conNombres(int anio) {
		return POR_ANIO.computeIfAbsent(anio, FeriadosNacionales::calcular);
	}

	/** Las 16 fechas del año. */
	public static Set<LocalDate> de(int anio) {
		return conNombres(anio).keySet();
	}

	public static boolean es(LocalDate fecha) {
		return conNombres(fecha.getYear()).containsKey(fecha);
	}

	/** Domingo de Pascua (gregoriano). 2026: 5 de abril; 2027: 28 de marzo. */
	public static LocalDate pascua(int anio) {
		int a = anio % 19;
		int b = anio / 100;
		int c = anio % 100;
		int d = b / 4;
		int e = b % 4;
		int f = (b + 8) / 25;
		int g = (b - f + 1) / 3;
		int h = (19 * a + b - d - g + 15) % 30;
		int i = c / 4;
		int k = c % 4;
		int l = (32 + 2 * e + 2 * i - h - k) % 7;
		int m = (a + 11 * h + 22 * l) / 451;
		int mes = (h + l - 7 * m + 114) / 31;
		int dia = (h + l - 7 * m + 114) % 31 + 1;
		return LocalDate.of(anio, mes, dia);
	}

	private static Map<LocalDate, String> calcular(int anio) {
		java.util.TreeMap<LocalDate, String> fechas = new java.util.TreeMap<>();
		FIJOS.forEach((dia, nombre) -> fechas.put(dia.atYear(anio), nombre));
		LocalDate pascua = pascua(anio);
		fechas.put(pascua.minusDays(3), "Jueves Santo");
		fechas.put(pascua.minusDays(2), "Viernes Santo");
		return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fechas));
	}
}
