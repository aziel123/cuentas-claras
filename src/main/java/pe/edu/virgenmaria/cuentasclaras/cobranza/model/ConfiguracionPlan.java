package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Montos y fechas de un plan de pensiones. Es un valor puro: {@link #validar(AnioEscolar)} aplica las reglas de la
 * sección 10 del diseño y devuelve la configuración con los montos en escala 2.
 *
 * @param montoMatricula       0.00 (no se cobra) hasta el monto de una pensión (DS 005-2021-MINEDU)
 * @param vencimientoMatricula entre el 01/01 del año anterior y el fin de clases
 * @param montoPension         0.01 a 99,999.99
 * @param vencimientos         de 1 a 12, una por mes, en orden, dentro del año y desde el mes de inicio de clases
 * @param cobroDesde           opcional: día 1 de un mes del año; lo anterior entra como saldo inicial
 */
public record ConfiguracionPlan(BigDecimal montoMatricula, LocalDate vencimientoMatricula, BigDecimal montoPension,
		List<LocalDate> vencimientos, LocalDate cobroDesde) {

	public static final int MAX_PENSIONES = 12;

	/** Pensiones de marzo a diciembre (el último día de cada mes) y matrícula al último día de febrero. */
	public static ConfiguracionPlan porDefecto(int anio, BigDecimal montoMatricula, BigDecimal montoPension) {
		return new ConfiguracionPlan(montoMatricula, Calendario.ultimoDiaDelMes(anio, 2), montoPension,
				Calendario.vencimientosPorDefecto(anio, 3, 10), null);
	}

	public ConfiguracionPlan {
		vencimientos = vencimientos == null ? List.of() : List.copyOf(vencimientos.stream().filter(Objects::nonNull).toList());
	}

	/**
	 * @return la misma configuración con los montos normalizados a escala 2
	 * @throws ReglaNegocioException con un mensaje en lenguaje claro si algo no cumple
	 */
	public ConfiguracionPlan validar(AnioEscolar anio) {
		int a = anio.getAnio();
		BigDecimal pension = Dinero.positivo(montoPension, "la pensión");
		BigDecimal matricula = Dinero.enRango(montoMatricula, Dinero.CERO, Dinero.MAXIMO, "la matrícula");
		if (matricula.compareTo(pension) > 0) {
			throw new ReglaNegocioException("La matrícula (" + Dinero.formatear(matricula)
					+ ") no puede ser mayor que una pensión (" + Dinero.formatear(pension)
					+ "): lo prohíbe el DS 005-2021-MINEDU.");
		}
		validarVencimientos(anio);
		if (vencimientoMatricula == null) {
			throw new ReglaNegocioException("Indica el vencimiento de la matrícula.");
		}
		LocalDate primeraMatricula = LocalDate.of(a - 1, 1, 1);
		if (vencimientoMatricula.isBefore(primeraMatricula) || vencimientoMatricula.isAfter(anio.getFinClases())) {
			throw new ReglaNegocioException("La matrícula debe vencer entre el " + Calendario.formatear(primeraMatricula)
					+ " y el fin de clases (" + Calendario.formatear(anio.getFinClases()) + ").");
		}
		if (cobroDesde != null && (cobroDesde.getDayOfMonth() != 1 || cobroDesde.getYear() != a)) {
			throw new ReglaNegocioException("«Cobrar desde» debe ser el día 1 de un mes de " + a
					+ " (por ejemplo 01/12/" + a + ").");
		}
		return new ConfiguracionPlan(matricula, vencimientoMatricula, pension, vencimientos, cobroDesde);
	}

	private void validarVencimientos(AnioEscolar anio) {
		int a = anio.getAnio();
		if (vencimientos.isEmpty() || vencimientos.size() > MAX_PENSIONES) {
			throw new ReglaNegocioException("El plan debe tener de 1 a " + MAX_PENSIONES + " pensiones.");
		}
		YearMonth inicioClases = YearMonth.from(anio.getInicioClases());
		YearMonth anterior = null;
		for (LocalDate vencimiento : vencimientos) {
			if (vencimiento.getYear() != a) {
				throw new ReglaNegocioException("La pensión con vencimiento " + Calendario.formatear(vencimiento)
						+ " está fuera del año " + a + ".");
			}
			YearMonth mes = YearMonth.from(vencimiento);
			if (mes.isBefore(inicioClases)) {
				throw new ReglaNegocioException("La pensión de " + Calendario.nombreMes(mes.getMonthValue())
						+ " vence antes del inicio de clases (" + Calendario.formatear(anio.getInicioClases())
						+ "): no se cobra por adelantado.");
			}
			if (anterior != null && !mes.isAfter(anterior)) {
				throw new ReglaNegocioException("Los vencimientos deben ir en orden y ser uno por mes: revisa "
						+ Calendario.formatear(vencimiento) + ".");
			}
			anterior = mes;
		}
	}

	/** «setiembre» si el plan cobra desde setiembre; vacío si cobra todo. */
	public String descripcionCobroDesde() {
		return cobroDesde == null ? "" : Calendario.nombreMes(cobroDesde.getMonthValue()) + " " + cobroDesde.getYear();
	}
}
