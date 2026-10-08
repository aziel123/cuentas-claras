package pe.edu.virgenmaria.cuentasclaras.panel.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalDate;

/** Rango de fechas de un reporte de ingresos: como máximo 12 meses (decisión 73). */
record RangoReporte(LocalDate desde, LocalDate hasta) {

	static final int MAX_MESES = 12;

	/** Sin fechas: el mes de {@code hoy} hasta hoy. */
	static RangoReporte de(LocalDate desde, LocalDate hasta, LocalDate hoy) {
		LocalDate inicio = desde != null ? desde : hoy.withDayOfMonth(1);
		LocalDate fin = hasta != null ? hasta : desde != null ? inicio.withDayOfMonth(inicio.lengthOfMonth()) : hoy;
		if (fin.isBefore(inicio)) {
			throw new ReglaNegocioException("La fecha «hasta» no puede ser anterior a «desde».");
		}
		if (!fin.isBefore(inicio.plusMonths(MAX_MESES))) {
			throw new ReglaNegocioException("Elige un rango de " + MAX_MESES + " meses como máximo.");
		}
		return new RangoReporte(inicio, fin);
	}
}
