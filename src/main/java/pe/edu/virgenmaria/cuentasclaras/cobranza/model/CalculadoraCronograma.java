package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Qué cuotas le tocan a una matrícula según el plan (pura: sin base, sin reloj). Reglas de la sección 10:
 * <ul>
 *   <li>{@code inicioCobro} = el mayor entre {@code cobroDesde} y el primer día del mes de la matrícula;</li>
 *   <li>pensión k si su vencimiento es igual o posterior a {@code inicioCobro};</li>
 *   <li>matrícula si su monto es mayor que cero y ({@code cobroDesde} es nulo o la matrícula vence desde
 *       {@code cobroDesde}); vence el mayor entre su vencimiento y la fecha de matrícula.</li>
 * </ul>
 * Los montos son exactamente los del plan: no hay divisiones ni redondeos.
 */
public final class CalculadoraCronograma {

	private CalculadoraCronograma() {
	}

	public static List<CuotaPlanificada> calcular(PlanPension plan, long matriculaId, LocalDate fechaMatricula) {
		return calcular(plan.configuracion(), plan.getAnioEscolar().getAnio(), matriculaId, fechaMatricula,
				plan.getAnioEscolar().getInicioClases());
	}

	public static List<CuotaPlanificada> calcular(ConfiguracionPlan plan, int anio, long matriculaId,
			LocalDate fechaMatricula) {
		return calcular(plan, anio, matriculaId, fechaMatricula, null);
	}

	/**
	 * @param inicioClases si la matrícula es de esa fecha o anterior es un ingreso regular: cronograma completo y la
	 *                     matrícula vence cuando dice el plan. Solo un ingreso posterior (tardío, aprobado por otra
	 *                     persona) recorta pensiones. {@code null}: se usa solo la fecha de matrícula.
	 */
	public static List<CuotaPlanificada> calcular(ConfiguracionPlan plan, int anio, long matriculaId,
			LocalDate fechaMatricula, LocalDate inicioClases) {
		Objects.requireNonNull(fechaMatricula, "fechaMatricula");
		if (inicioClases != null && !fechaMatricula.isAfter(inicioClases)) {
			fechaMatricula = plan.vencimientoMatricula().isBefore(fechaMatricula) ? plan.vencimientoMatricula()
					: fechaMatricula;
		}
		LocalDate inicioCobro = Calendario.primerDiaDelMes(fechaMatricula);
		if (plan.cobroDesde() != null && plan.cobroDesde().isAfter(inicioCobro)) {
			inicioCobro = plan.cobroDesde();
		}
		List<CuotaPlanificada> cuotas = new ArrayList<>();
		if (plan.montoMatricula().signum() > 0
				&& (plan.cobroDesde() == null || !plan.vencimientoMatricula().isBefore(plan.cobroDesde()))) {
			LocalDate vence = plan.vencimientoMatricula().isBefore(fechaMatricula) ? fechaMatricula
					: plan.vencimientoMatricula();
			cuotas.add(new CuotaPlanificada(TipoCuota.MATRICULA, null, "Matrícula " + anio, plan.montoMatricula(),
					vence, claveMatricula(matriculaId), Obligaciones.matricula(anio)));
		}
		for (LocalDate vencimiento : plan.vencimientos()) {
			if (vencimiento.isBefore(inicioCobro)) {
				continue;
			}
			int mes = vencimiento.getMonthValue();
			cuotas.add(new CuotaPlanificada(TipoCuota.PENSION, mes, descripcionPension(anio, mes), plan.montoPension(),
					vencimiento, clavePension(matriculaId, mes), Obligaciones.pension(anio, mes)));
		}
		return List.copyOf(cuotas);
	}

	/** «Pensión setiembre 2027» (meses en español del Perú). */
	public static String descripcionPension(int anio, int mes) {
		return "Pensión " + Calendario.nombreMes(mes) + " " + anio;
	}

	public static String claveMatricula(long matriculaId) {
		return "MAT:" + matriculaId;
	}

	public static String clavePension(long matriculaId, int mes) {
		return "PEN:" + matriculaId + ":" + mes;
	}
}
