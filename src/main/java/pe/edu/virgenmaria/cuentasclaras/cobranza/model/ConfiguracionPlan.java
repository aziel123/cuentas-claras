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

	/**
	 * Sprint 11, tanda 1b (F-D2): una pensión por mes, el último día de cada mes, desde el mes del inicio de clases hasta el
	 * mes del fin de clases (dentro del año del plan y como máximo {@link #MAX_PENSIONES}); la matrícula, al último día de
	 * febrero. Si las clases empiezan a mitad de mes (16/03), la primera pensión es la de ese mes (31/03), que
	 * {@link #validar} acepta porque compara por mes. Antes era siempre de marzo a diciembre.
	 */
	public static ConfiguracionPlan porDefecto(AnioEscolar anio, BigDecimal montoMatricula, BigDecimal montoPension) {
		int a = anio.getAnio();
		YearMonth primero = YearMonth.from(anio.getInicioClases());
		YearMonth ultimo = YearMonth.from(anio.getFinClases());
		if (primero.getYear() < a) {
			primero = YearMonth.of(a, 1);
		}
		if (ultimo.getYear() > a) {
			ultimo = YearMonth.of(a, 12);
		}
		int cantidad = Math.max(1, Math.min(MAX_PENSIONES, (int) primero.until(ultimo, java.time.temporal.ChronoUnit.MONTHS) + 1));
		return new ConfiguracionPlan(montoMatricula, Calendario.ultimoDiaDelMes(a, 2), montoPension,
				Calendario.vencimientosPorDefecto(a, primero.getMonthValue(), cantidad), null);
	}

	/** Sprint 11, tanda 1b (F-D3): un rango de fechas válido (los {@code min} y {@code max} del formulario). */
	public record RangoFechas(LocalDate desde, LocalDate hasta) {

		public boolean contiene(LocalDate fecha) {
			return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
		}

		/** «Entre el 01/01/2025 y el 18/12/2026». */
		public String enPalabras() {
			return "Entre el " + Calendario.formatear(desde) + " y el " + Calendario.formatear(hasta);
		}
	}

	/** Vencimiento de la matrícula: del 01/01 del año anterior al fin de clases. */
	public static RangoFechas rangoMatricula(AnioEscolar anio) {
		return new RangoFechas(LocalDate.of(anio.getAnio() - 1, 1, 1), anio.getFinClases());
	}

	/** Cada pensión: del día 1 del mes de inicio de clases (o del 01/01, si empezó el año anterior) al 31/12 del plan. */
	public static RangoFechas rangoPensiones(AnioEscolar anio) {
		LocalDate inicio = anio.getInicioClases().withDayOfMonth(1);
		LocalDate enero = LocalDate.of(anio.getAnio(), 1, 1);
		return new RangoFechas(inicio.isBefore(enero) ? enero : inicio, LocalDate.of(anio.getAnio(), 12, 31));
	}

	/** «Cobrar desde»: del 01/01 al 31/12 del año del plan (que sea el día 1 de un mes lo valida solo el servidor). */
	public static RangoFechas rangoCobroDesde(AnioEscolar anio) {
		return new RangoFechas(LocalDate.of(anio.getAnio(), 1, 1), LocalDate.of(anio.getAnio(), 12, 31));
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
		RangoFechas rangoMatricula = rangoMatricula(anio);
		if (!rangoMatricula.contiene(vencimientoMatricula)) {
			throw new ReglaNegocioException("La matrícula debe vencer entre el " + Calendario.formatear(rangoMatricula.desde())
					+ " y el fin de clases (" + Calendario.formatear(rangoMatricula.hasta()) + ").");
		}
		if (cobroDesde != null && (cobroDesde.getDayOfMonth() != 1 || !rangoCobroDesde(anio).contiene(cobroDesde))) {
			throw new ReglaNegocioException("«Cobrar desde» debe ser el día 1 de un mes de " + a
					+ " (por ejemplo 01/12/" + a + ").");
		}
		if (cobroDesde != null && vencimientos.stream().allMatch(v -> v.isBefore(cobroDesde))) {
			throw new ReglaNegocioException("Cobrando desde el " + Calendario.formatear(cobroDesde)
					+ " no se cobraría ninguna pensión del plan: la última vence el "
					+ Calendario.formatear(vencimientos.get(vencimientos.size() - 1)) + ".");
		}
		return new ConfiguracionPlan(matricula, vencimientoMatricula, pension, vencimientos, cobroDesde);
	}

	private void validarVencimientos(AnioEscolar anio) {
		int a = anio.getAnio();
		if (vencimientos.isEmpty() || vencimientos.size() > MAX_PENSIONES) {
			throw new ReglaNegocioException("El plan debe tener de 1 a " + MAX_PENSIONES + " pensiones.");
		}
		RangoFechas rango = rangoPensiones(anio);
		YearMonth anterior = null;
		for (LocalDate vencimiento : vencimientos) {
			if (vencimiento.isAfter(rango.hasta()) || vencimiento.getYear() != a) {
				throw new ReglaNegocioException("La pensión con vencimiento " + Calendario.formatear(vencimiento)
						+ " está fuera del año " + a + ".");
			}
			YearMonth mes = YearMonth.from(vencimiento);
			if (vencimiento.isBefore(rango.desde())) {
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
