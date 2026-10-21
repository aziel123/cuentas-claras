package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoDia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AvanceMes;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Porcentaje;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarraApilada;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarrasHorizontales;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarrasVerticales;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Geometria;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Kpi;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Kpi.Tendencia;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.TableroPromotoria;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Plural;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Rediseño de la interfaz: el tablero del inicio de Promotoría (el usuario pidió «un reporte con gráficos para que no se
 * vea todo texto»). Cuatro cifras con su comparación, lo cobrado en los últimos 30 días, la morosidad por grado y los
 * medios de pago del mes. No tiene consultas propias: usa los puertos que ya usan el panel y los reportes
 * ({@link CifrasCaja}, {@link CifrasCobranza} y la bandeja), que filtra {@code @TenantId} por el colegio en sesión. Solo
 * Promotoría: Caja no ve montos agregados.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('PROMOTOR')")
public class GraficosPromotoria implements TableroPromotoria {

	/** Días de la serie diaria, hoy incluido. */
	static final int DIAS = 30;

	private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

	/** Medios que el tablero junta como «Banco»; el resto, menos el efectivo, es «Digital». */
	private static final Set<MedioPago> BANCO = EnumSet.of(MedioPago.RECAUDACION_BANCARIA, MedioPago.TRANSFERENCIA);

	private final CifrasCaja caja;

	private final CifrasCobranza cobranza;

	private final BandejaAprobaciones bandeja;

	private final Clock reloj;

	public GraficosPromotoria(CifrasCaja caja, CifrasCobranza cobranza, BandejaAprobaciones bandeja, Clock reloj) {
		this.caja = caja;
		this.cobranza = cobranza;
		this.bandeja = bandeja;
		this.reloj = reloj;
	}

	@Override
	public Tablero tablero() {
		LocalDate hoy = LocalDate.now(reloj);
		CobradoPeriodo mes = caja.cobrado(hoy.withDayOfMonth(1), hoy);
		List<MorosidadGrado> morosidad = cobranza.anio(null).map(a -> cobranza.morosidadPorGrado(a.id(), hoy))
				.orElse(List.of());
		return new Tablero(kpis(hoy, mes, morosidad), diaria(hoy), GraficosCobranza.morosidadPorGrado(morosidad,
				cobranza.anio(null).map(AnioOpcion::anio).orElse(null)), medios(hoy, mes));
	}

	private List<Kpi> kpis(LocalDate hoy, CobradoPeriodo mes, List<MorosidadGrado> morosidad) {
		List<Kpi> kpis = new ArrayList<>();
		kpis.add(cobradoHoy(hoy));
		kpis.add(cobradoMes(hoy, mes));
		kpis.add(GraficosCobranza.kpiMorosidad(morosidad));
		long pendientes = bandeja.contarPorResolver();
		kpis.add(new Kpi("Pendientes por aprobar", String.valueOf(pendientes),
				pendientes == 0 ? "Nada espera tu aprobación" : Plural.de(pendientes, "solicitud espera", "solicitudes esperan")
						+ " tu decisión", Tendencia.NINGUNA, null, "/aprobaciones", "Ir a Aprobaciones"));
		return kpis;
	}

	/** Lo cobrado hoy frente al mismo día del mes pasado (el 21/10 frente al 21/09; el 31/03 frente al 28/02). */
	private Kpi cobradoHoy(LocalDate hoy) {
		BigDecimal total = caja.cobrado(hoy, hoy).total();
		LocalDate antes = hoy.minusMonths(1);
		BigDecimal previo = caja.cobrado(antes, antes).total();
		String fecha = antes.format(DIA_MES);
		String comparacion;
		Tendencia tendencia;
		int signo = total.compareTo(previo);
		if (previo.signum() == 0) {
			comparacion = total.signum() == 0 ? "Ni hoy ni el " + fecha + " hubo cobros"
					: "El " + fecha + " no hubo cobros";
			tendencia = total.signum() == 0 ? Tendencia.IGUAL : Tendencia.SUBE;
		}
		else if (signo == 0) {
			comparacion = "Igual que el " + fecha + " (" + Dinero.formatear(previo) + ")";
			tendencia = Tendencia.IGUAL;
		}
		else {
			long variacion = total.subtract(previo).abs().multiply(BigDecimal.valueOf(100))
					.divide(previo, 0, RoundingMode.HALF_UP).longValue();
			comparacion = variacion + " % " + (signo > 0 ? "más" : "menos") + " que el " + fecha + " ("
					+ Dinero.formatear(previo) + ")";
			tendencia = signo > 0 ? Tendencia.SUBE : Tendencia.BAJA;
		}
		return new Kpi("Cobrado hoy", Dinero.formatear(total), comparacion, tendencia);
	}

	/** Lo cobrado en el mes frente a la meta: lo que vence en el mes (lo emitido, sin lo anulado ni los descuentos). */
	private Kpi cobradoMes(LocalDate hoy, CobradoPeriodo mes) {
		AvanceMes avance = cobranza.avanceDelMes(YearMonth.from(hoy));
		OptionalLong porcentaje = avance.porcentaje();
		String comparacion = avance.vence().signum() == 0 ? "En " + nombreMes(hoy) + " no vence ninguna cuota"
				: "Meta: " + Dinero.formatear(avance.vence()) + " que vence en " + nombreMes(hoy) + " · pagado "
						+ porcentaje.getAsLong() + " %";
		return new Kpi("Cobrado en " + nombreMes(hoy), Dinero.formatear(mes.total()), comparacion, Tendencia.NINGUNA,
				Geometria.porcentaje(avance.pagado(), avance.vence()), null, null);
	}

	/** Cada uno de los últimos 30 días, con 0 en los días sin cobros (también se dibujan: un hueco es información). */
	BarrasVerticales diaria(LocalDate hoy) {
		LocalDate desde = hoy.minusDays(DIAS - 1L);
		Map<LocalDate, CobradoDia> porDia = caja.cobradoPorDia(desde, hoy);
		BigDecimal maximo = porDia.values().stream().map(CobradoDia::total).max(BigDecimal::compareTo)
				.orElse(Dinero.CERO);
		BigDecimal tope = Geometria.topeRedondo(maximo);
		BigDecimal ranura = BigDecimal.valueOf(100).divide(BigDecimal.valueOf(DIAS), 6, RoundingMode.HALF_UP);
		BigDecimal ancho = ranura.multiply(new BigDecimal("0.64"));
		List<BarrasVerticales.Barra> barras = new ArrayList<>();
		List<BarrasVerticales.Rotulo> rotulos = new ArrayList<>();
		BigDecimal suma = Dinero.CERO;
		int conCobros = 0;
		LocalDate mejor = null;
		for (int i = 0; i < DIAS; i++) {
			LocalDate dia = desde.plusDays(i);
			CobradoDia c = porDia.get(dia);
			BigDecimal total = c == null ? Dinero.CERO : c.total();
			long pagos = c == null ? 0 : c.cantidad();
			suma = suma.add(total);
			if (total.signum() > 0) {
				conCobros++;
				if (mejor == null || total.compareTo(porDia.get(mejor).total()) > 0) {
					mejor = dia;
				}
			}
			BigDecimal alto = new BigDecimal(Geometria.porcentaje(total, tope));
			BigDecimal x = ranura.multiply(BigDecimal.valueOf(i)).add(ranura.subtract(ancho).divide(BigDecimal.TWO, 6,
					RoundingMode.HALF_UP));
			barras.add(new BarrasVerticales.Barra(Geometria.numero(x), Geometria.numero(ancho),
					Geometria.numero(BigDecimal.valueOf(100).subtract(alto)), Geometria.numero(alto),
					Calendario.formatear(dia) + (dia.equals(hoy) ? " (hoy)" : ""), Dinero.formatear(total),
					Plural.de(pagos, "pago", "pagos")));
			if (i == 0 || i == DIAS / 2 || i == DIAS - 1) {
				BigDecimal centro = ranura.multiply(BigDecimal.valueOf(i)).add(ranura.divide(BigDecimal.TWO, 6,
						RoundingMode.HALF_UP));
				rotulos.add(new BarrasVerticales.Rotulo(Geometria.numero(centro), i == DIAS - 1 ? "Hoy" : dia.format(DIA_MES),
						i == 0 ? "start" : i == DIAS - 1 ? "end" : "middle"));
			}
		}
		BigDecimal mitad = tope.divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
		List<BarrasVerticales.Guia> guias = List.of(new BarrasVerticales.Guia("0.00", eje(tope)),
				new BarrasVerticales.Guia("50.00", eje(mitad)), new BarrasVerticales.Guia("100.00", "S/ 0"));
		String resumen = conCobros == 0 ? "En los últimos 30 días no hubo cobros."
				: "En los últimos 30 días se cobraron " + Dinero.formatear(suma) + " en "
						+ Plural.de(conCobros, "día con cobros", "días con cobros") + ". El día con más cobros fue el "
						+ Calendario.formatear(mejor) + ", con " + Dinero.formatear(porDia.get(mejor).total()) + ".";
		return new BarrasVerticales("grafico-diaria", "Cobranza de los últimos 30 días", resumen, guias, barras, rotulos,
				List.of("Día", "Cobrado", "Pagos"));
	}

	/** Lo cobrado en el mes por medio, en un orden fijo: efectivo, digital y banco. */
	BarraApilada medios(LocalDate hoy, CobradoPeriodo mes) {
		BigDecimal[] montos = { Dinero.CERO, Dinero.CERO, Dinero.CERO };
		long[] pagos = new long[3];
		for (CobradoPeriodo.PorMedio m : mes.porMedio()) {
			int i = m.medio() == MedioPago.EFECTIVO ? 0 : BANCO.contains(m.medio()) ? 2 : 1;
			montos[i] = montos[i].add(m.total());
			pagos[i] += m.cantidad();
		}
		String[] etiquetas = { "Efectivo", "Digital (Yape, Plin y tarjeta)", "Banco (código de alumno y transferencia)" };
		List<BarraApilada.Segmento> segmentos = new ArrayList<>();
		BigDecimal acumulado = BigDecimal.ZERO;
		for (int i = 0; i < 3; i++) {
			BigDecimal ancho = new BigDecimal(Geometria.porcentaje(montos[i], mes.total()));
			OptionalLong p = Porcentaje.de(montos[i], mes.total());
			segmentos.add(new BarraApilada.Segmento(i + 1, etiquetas[i], Geometria.numero(acumulado),
					Geometria.numero(ancho), Dinero.formatear(montos[i]), p.isPresent() ? p.getAsLong() + " %" : "—",
					Plural.de(pagos[i], "pago", "pagos")));
			acumulado = acumulado.add(ancho);
		}
		String resumen = mes.total().signum() == 0 ? "En " + nombreMes(hoy) + " todavía no hay cobros."
				: "De " + Dinero.formatear(mes.total()) + " cobrados en " + nombreMes(hoy) + ": efectivo "
						+ segmentos.get(0).porcentaje() + ", digital " + segmentos.get(1).porcentaje() + " y banco "
						+ segmentos.get(2).porcentaje() + ".";
		return new BarraApilada("grafico-medios", "Medios de pago de " + nombreMes(hoy), resumen,
				Dinero.formatear(mes.total()), segmentos, List.of("Medio", "Cobrado", "Parte", "Pagos"),
				"En " + nombreMes(hoy) + " todavía no hay cobros.");
	}

	/** «S/ 2,000» en el eje: sin céntimos, que en un eje solo estorban. */
	private static String eje(BigDecimal monto) {
		String texto = Dinero.formatear(monto);
		return texto.endsWith(".00") ? texto.substring(0, texto.length() - 3) : texto;
	}

	private static String nombreMes(LocalDate fecha) {
		return Calendario.nombreMes(fecha.getMonthValue()).toLowerCase(java.util.Locale.ROOT);
	}
}
