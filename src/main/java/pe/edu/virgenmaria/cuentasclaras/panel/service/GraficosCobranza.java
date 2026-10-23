package pe.edu.virgenmaria.cuentasclaras.panel.service;

import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Porcentaje;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarrasHorizontales;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Geometria;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Kpi;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Plural;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Lo que comparten los tableros de Promotoría y de Dirección sobre la morosidad. La morosidad es la parte de los alumnos
 * matriculados que tiene alguna cuota vencida y sin pagar (la misma fila del reporte de morosidad por grado). Nunca por
 * sección (Ley 29733): como mucho por grado.
 */
final class GraficosCobranza {

	private GraficosCobranza() {
	}

	/** La tarjeta «Morosidad»: el % de alumnos con deuda vencida, con cuántos y cuánto. */
	static Kpi kpiMorosidad(List<MorosidadGrado> filas) {
		long matriculados = filas.stream().filter(f -> f.grado() != null && !f.retirados())
				.mapToLong(MorosidadGrado::matriculados).sum();
		long conDeuda = filas.stream().filter(f -> f.grado() != null && !f.retirados())
				.mapToLong(MorosidadGrado::conDeuda).sum();
		BigDecimal monto = Dinero.sumar(filas.stream().map(MorosidadGrado::monto).toList());
		OptionalLong p = Porcentaje.de(conDeuda, matriculados);
		return new Kpi("Morosidad", p.isPresent() ? p.getAsLong() + " %" : "—",
				matriculados == 0 ? "Sin alumnos matriculados en el año"
						: conDeuda + " de " + Plural.de(matriculados, "alumno", "alumnos") + " con deuda vencida · "
								+ Dinero.formatear(monto),
				Kpi.Tendencia.NINGUNA, null, "/panel/reportes/morosidad", "Ver morosidad por grado");
	}

	/** Barras por grado; las filas de retirados y sin matrícula van al final, con su monto y sin % (no tienen base). */
	static BarrasHorizontales morosidadPorGrado(List<MorosidadGrado> filas, Integer anio) {
		List<BarrasHorizontales.Fila> barras = new ArrayList<>();
		for (MorosidadGrado f : filas) {
			barras.add(fila(f.etiqueta(), f.conDeuda(), f.matriculados(), f.monto(), f.grado() != null && !f.retirados()));
		}
		return grafico("grafico-morosidad", "Morosidad por grado" + (anio == null ? "" : " · " + anio), barras,
				"Grado");
	}

	/** Barras por nivel (Inicial, Primaria y Secundaria): el resumen de cobranza de Dirección, sin detalle por alumno. */
	static BarrasHorizontales morosidadPorNivel(List<MorosidadGrado> filas, Integer anio) {
		Map<Nivel, long[]> porNivel = new EnumMap<>(Nivel.class);
		Map<Nivel, BigDecimal> montos = new EnumMap<>(Nivel.class);
		for (MorosidadGrado f : filas) {
			if (f.grado() == null || f.retirados()) {
				continue;
			}
			long[] c = porNivel.computeIfAbsent(f.grado().nivel(), n -> new long[2]);
			c[0] += f.conDeuda();
			c[1] += f.matriculados();
			montos.merge(f.grado().nivel(), f.monto(), BigDecimal::add);
		}
		List<BarrasHorizontales.Fila> barras = new ArrayList<>();
		porNivel.forEach((nivel, c) -> barras.add(fila(nivel.etiqueta(), c[0], c[1], montos.get(nivel), true)));
		return grafico("grafico-morosidad-nivel", "Morosidad por nivel" + (anio == null ? "" : " · " + anio), barras,
				"Nivel");
	}

	private static BarrasHorizontales.Fila fila(String etiqueta, long conDeuda, long matriculados, BigDecimal monto,
			boolean conBase) {
		OptionalLong p = conBase ? Porcentaje.de(conDeuda, matriculados) : OptionalLong.empty();
		String ancho = p.isPresent() ? Geometria.porcentaje(BigDecimal.valueOf(conDeuda), BigDecimal.valueOf(matriculados))
				: "0.00";
		String detalle = conBase ? conDeuda + " de " + Plural.de(matriculados, "alumno", "alumnos")
				: Plural.de(conDeuda, "alumno", "alumnos") + " con deuda";
		return new BarrasHorizontales.Fila(etiqueta, ancho, p.isPresent() ? p.getAsLong() + " %" : "—", detalle,
				Dinero.formatear(monto));
	}

	private static BarrasHorizontales grafico(String id, String titulo, List<BarrasHorizontales.Fila> barras,
			String columna) {
		String resumen;
		if (barras.isEmpty()) {
			resumen = "No hay alumnos matriculados ni deuda vencida en el año.";
		}
		else {
			BarrasHorizontales.Fila mayor = barras.stream().filter(b -> !"—".equals(b.valor()))
					.max((a, b) -> new BigDecimal(a.ancho()).compareTo(new BigDecimal(b.ancho()))).orElse(null);
			resumen = mayor == null || "0.00".equals(mayor.ancho()) ? "Ningún alumno matriculado tiene deuda vencida."
					: "La morosidad más alta es la de " + mayor.etiqueta() + ": " + mayor.valor() + " (" + mayor.detalle()
							+ ", " + mayor.monto() + ").";
		}
		return new BarrasHorizontales(id, titulo, resumen, barras, List.of(columna, "Morosidad", "Alumnos con deuda",
				"Deuda vencida"), "No hay alumnos matriculados en el año.");
	}
}
