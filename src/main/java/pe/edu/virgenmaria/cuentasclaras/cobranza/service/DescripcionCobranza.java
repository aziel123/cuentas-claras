package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import pe.edu.virgenmaria.cuentasclaras.cobranza.model.CuotaPlanificada;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.PlanPension;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** Textos para la bitácora: montos y vencimientos completos, en lenguaje claro. */
final class DescripcionCobranza {

	private DescripcionCobranza() {
	}

	/** «Matrícula S/ 350.00 (vence 28/02/2027) · Pensión S/ 450.00 × 10: 31/03/2027, … · Cobra desde 01/12/2026». */
	static String plan(PlanPension plan) {
		String texto = "Matrícula " + Dinero.formatear(plan.getMontoMatricula()) + " (vence "
				+ Calendario.formatear(plan.getVencimientoMatricula()) + ") · Pensión "
				+ Dinero.formatear(plan.getMontoPension()) + " × " + plan.getVencimientos().size() + ": "
				+ fechas(plan.getVencimientos());
		if (plan.getCobroDesde() != null) {
			texto += " · Cobra desde " + Calendario.formatear(plan.getCobroDesde());
		}
		return texto;
	}

	static String cuotas(List<CuotaPlanificada> cuotas) {
		return cuotas.stream()
				.map(c -> c.descripcion() + " " + Dinero.formatear(c.monto()) + " vence "
						+ Calendario.formatear(c.vencimiento()))
				.collect(Collectors.joining("; "));
	}

	static String fechas(List<LocalDate> fechas) {
		return fechas.stream().map(Calendario::formatear).collect(Collectors.joining(", "));
	}
}
