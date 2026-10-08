package pe.edu.virgenmaria.cuentasclaras.panel.service;

import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalDate;

/**
 * Rango de fechas de un reporte de ingresos: como máximo {@code cuentasclaras.panel.exportacion-max-meses} (12 por
 * defecto, decisión 73).
 */
record RangoReporte(LocalDate desde, LocalDate hasta) {

	/** Sin fechas: el mes de {@code hoy} hasta hoy. */
	static RangoReporte de(LocalDate desde, LocalDate hasta, LocalDate hoy, int maxMeses) {
		LocalDate inicio = desde != null ? desde : hoy.withDayOfMonth(1);
		LocalDate fin = hasta != null ? hasta : desde != null ? inicio.withDayOfMonth(inicio.lengthOfMonth()) : hoy;
		if (fin.isBefore(inicio)) {
			throw new ReglaNegocioException("La fecha «hasta» no puede ser anterior a «desde».");
		}
		if (!fin.isBefore(inicio.plusMonths(maxMeses))) {
			throw new ReglaNegocioException("Elige un rango de " + maxMeses + " meses como máximo.");
		}
		return new RangoReporte(inicio, fin);
	}
}
