package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoDia;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AvanceMes;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.Tramos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarraApilada;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarrasHorizontales;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarrasVerticales;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Geometria;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Kpi;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.TableroPromotoria;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El tablero del inicio de Promotoría con datos conocidos: las series (30 días, morosidad por grado y medios del mes) y
 * las cuatro cifras salen de los puertos de caja, cobranza y la bandeja, sin consultas propias.
 */
class GraficosPromotoriaTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 21);

	private final CifrasCaja caja = mock(CifrasCaja.class);

	private final CifrasCobranza cobranza = mock(CifrasCobranza.class);

	private final BandejaAprobaciones bandeja = mock(BandejaAprobaciones.class);

	private GraficosPromotoria graficos;

	@BeforeEach
	void preparar() {
		Clock reloj = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Lima"));
		graficos = new GraficosPromotoria(caja, cobranza, bandeja, reloj);
		Map<LocalDate, CobradoDia> porDia = new TreeMap<>();
		porDia.put(LocalDate.of(2026, 9, 22), dia("2026-09-22", "500.00", 2));
		porDia.put(LocalDate.of(2026, 10, 5), dia("2026-10-05", "1830.00", 6));
		porDia.put(HOY, dia("2026-10-21", "900.00", 3));
		when(caja.cobradoPorDia(LocalDate.of(2026, 9, 22), HOY)).thenReturn(porDia);
		when(caja.cobrado(HOY, HOY)).thenReturn(periodo(HOY, HOY, List.of(
				new CobradoPeriodo.PorMedio(MedioPago.EFECTIVO, 1, new BigDecimal("300.00")),
				new CobradoPeriodo.PorMedio(MedioPago.YAPE, 2, new BigDecimal("600.00")))));
		LocalDate mesPasado = LocalDate.of(2026, 9, 21);
		when(caja.cobrado(mesPasado, mesPasado)).thenReturn(periodo(mesPasado, mesPasado, List.of(
				new CobradoPeriodo.PorMedio(MedioPago.EFECTIVO, 2, new BigDecimal("750.00")))));
		when(caja.cobrado(LocalDate.of(2026, 10, 1), HOY)).thenReturn(periodo(LocalDate.of(2026, 10, 1), HOY, List.of(
				new CobradoPeriodo.PorMedio(MedioPago.EFECTIVO, 4, new BigDecimal("1000.00")),
				new CobradoPeriodo.PorMedio(MedioPago.YAPE, 3, new BigDecimal("900.00")),
				new CobradoPeriodo.PorMedio(MedioPago.TARJETA, 1, new BigDecimal("100.00")),
				new CobradoPeriodo.PorMedio(MedioPago.RECAUDACION_BANCARIA, 2, new BigDecimal("600.00")),
				new CobradoPeriodo.PorMedio(MedioPago.TRANSFERENCIA, 1, new BigDecimal("400.00")))));
		when(cobranza.avanceDelMes(YearMonth.of(2026, 10))).thenReturn(new AvanceMes(YearMonth.of(2026, 10),
				new BigDecimal("5000.00"), new BigDecimal("3200.00")));
		when(cobranza.anio(null)).thenReturn(Optional.of(new AnioOpcion(7L, 2026, "En curso")));
		when(cobranza.morosidadPorGrado(any(), any())).thenReturn(List.of(
				new MorosidadGrado(Grado.PRIMARIA_2, false, 20, 5, new BigDecimal("1350.00"), Tramos.NINGUNO),
				new MorosidadGrado(Grado.PRIMARIA_5, false, 10, 1, new BigDecimal("450.00"), Tramos.NINGUNO),
				new MorosidadGrado(null, true, 2, 1, new BigDecimal("200.00"), Tramos.NINGUNO)));
		when(bandeja.contarPorResolver()).thenReturn(3L);
	}

	@Test
	void laSerieDiariaTieneLosTreintaDiasConCeroDondeNoHuboCobros() {
		BarrasVerticales diaria = graficos.tablero().diaria();

		assertThat(diaria.barras()).hasSize(30);
		// Tope del eje: 1,830 → 2,000; la barra del 05/10 llega al 91.50 % del alto.
		assertThat(diaria.guias()).extracting(BarrasVerticales.Guia::texto).containsExactly("S/ 2,000", "S/ 1,000", "S/ 0");
		BarrasVerticales.Barra cinco = diaria.barras().get(13);
		assertThat(cinco.etiqueta()).isEqualTo("05/10/2026");
		assertThat(cinco.alto()).isEqualTo("91.50");
		assertThat(cinco.y()).isEqualTo("8.50");
		assertThat(cinco.valor()).isEqualTo("S/ 1,830.00");
		assertThat(cinco.detalle()).isEqualTo("6 pagos");
		assertThat(diaria.barras().get(1)).extracting(BarrasVerticales.Barra::alto, BarrasVerticales.Barra::valor,
				BarrasVerticales.Barra::detalle).containsExactly("0.00", "S/ 0.00", "0 pagos");
		assertThat(diaria.barras().getLast().etiqueta()).isEqualTo("21/10/2026 (hoy)");
		assertThat(diaria.rotulos()).extracting(BarrasVerticales.Rotulo::texto).containsExactly("22/09", "07/10", "Hoy");
		assertThat(diaria.resumen()).isEqualTo("En los últimos 30 días se cobraron S/ 3,230.00 en 3 días con cobros. El día "
				+ "con más cobros fue el 05/10/2026, con S/ 1,830.00.");
	}

	@Test
	void laMorosidadPorGradoVaDeCeroACienYLosRetiradosSinPorcentaje() {
		BarrasHorizontales morosidad = graficos.tablero().morosidad();

		assertThat(morosidad.titulo()).isEqualTo("Morosidad por grado · 2026");
		assertThat(morosidad.filas()).extracting(BarrasHorizontales.Fila::etiqueta, BarrasHorizontales.Fila::ancho,
				BarrasHorizontales.Fila::valor, BarrasHorizontales.Fila::detalle, BarrasHorizontales.Fila::monto)
				.containsExactly(
						tuple("2.° Primaria", "25.00", "25 %", "5 de 20 alumnos", "S/ 1,350.00"),
						tuple("5.° Primaria", "10.00", "10 %", "1 de 10 alumnos", "S/ 450.00"),
						tuple(MorosidadGrado.RETIRADOS, "0.00", "—", "1 alumno con deuda", "S/ 200.00"));
		assertThat(morosidad.resumen()).contains("2.° Primaria: 25 %");
	}

	@Test
	void losMediosDelMesVanEnOrdenFijoYSumanElTotal() {
		BarraApilada medios = graficos.tablero().medios();

		assertThat(medios.total()).isEqualTo("S/ 3,000.00");
		assertThat(medios.segmentos()).extracting(BarraApilada.Segmento::serie, BarraApilada.Segmento::x,
				BarraApilada.Segmento::ancho, BarraApilada.Segmento::monto, BarraApilada.Segmento::porcentaje,
				BarraApilada.Segmento::pagos).containsExactly(
						tuple(1, "0.00", "33.33", "S/ 1,000.00", "33 %", "4 pagos"),
						tuple(2, "33.33", "33.33", "S/ 1,000.00", "33 %", "4 pagos"),
						tuple(3, "66.66", "33.33", "S/ 1,000.00", "33 %", "3 pagos"));
	}

	@Test
	void lasCuatroCifrasTraenSuComparacion() {
		List<Kpi> kpis = graficos.tablero().kpis();

		assertThat(kpis).extracting(Kpi::etiqueta, Kpi::cifra, Kpi::comparacion, Kpi::tendencia).containsExactly(
				tuple("Cobrado hoy", "S/ 900.00", "20 % más que el 21/09 (S/ 750.00)", Kpi.Tendencia.SUBE),
				tuple("Cobrado en octubre", "S/ 3,000.00", "Meta: S/ 5,000.00 que vence en octubre · pagado 64 %",
						Kpi.Tendencia.NINGUNA),
				// Los retirados no cuentan en la base: 6 de 30 matriculados.
				tuple("Morosidad", "20 %", "6 de 30 alumnos con deuda vencida · S/ 2,000.00", Kpi.Tendencia.NINGUNA),
				tuple("Pendientes por aprobar", "3", "3 solicitudes esperan tu decisión", Kpi.Tendencia.NINGUNA));
		assertThat(kpis.get(1).avance()).isEqualTo("64.00");
	}

	@Test
	void sinCobrosNiDatosElTableroIgualTieneForma() {
		when(caja.cobradoPorDia(any(), any())).thenReturn(Map.of());
		when(caja.cobrado(any(), any())).thenReturn(periodo(HOY, HOY, List.of()));
		when(cobranza.anio(null)).thenReturn(Optional.empty());
		when(cobranza.avanceDelMes(any())).thenReturn(new AvanceMes(YearMonth.of(2026, 10), BigDecimal.ZERO.setScale(2),
				BigDecimal.ZERO.setScale(2)));
		when(bandeja.contarPorResolver()).thenReturn(0L);

		TableroPromotoria.Tablero tablero = graficos.tablero();

		assertThat(tablero.diaria().resumen()).isEqualTo("En los últimos 30 días no hubo cobros.");
		assertThat(tablero.diaria().guias()).extracting(BarrasVerticales.Guia::texto)
				.containsExactly("S/ 100", "S/ 50", "S/ 0");
		assertThat(tablero.medios().sinDatos()).isTrue();
		assertThat(tablero.morosidad().filas()).isEmpty();
		assertThat(tablero.kpis()).extracting(Kpi::cifra).containsExactly("S/ 0.00", "S/ 0.00", "—", "0");
		assertThat(tablero.kpis().getFirst().comparacion()).isEqualTo("Ni hoy ni el 21/09 hubo cobros");
	}

	@Test
	void elTopeDelEjeEsUnNumeroRedondo() {
		assertThat(Geometria.topeRedondo(new BigDecimal("1830.00"))).isEqualByComparingTo("2000");
		assertThat(Geometria.topeRedondo(new BigDecimal("2100.00"))).isEqualByComparingTo("2500");
		assertThat(Geometria.topeRedondo(new BigDecimal("4100.00"))).isEqualByComparingTo("5000");
		assertThat(Geometria.topeRedondo(new BigDecimal("450.00"))).isEqualByComparingTo("500");
		assertThat(Geometria.topeRedondo(new BigDecimal("1000.00"))).isEqualByComparingTo("1000");
		assertThat(Geometria.porcentaje(new BigDecimal("3"), new BigDecimal("2"))).isEqualTo("100.00");
	}

	private static CobradoDia dia(String fecha, String total, long pagos) {
		return new CobradoDia(LocalDate.parse(fecha), new BigDecimal(total), pagos, BigDecimal.ZERO.setScale(2), 0);
	}

	private static CobradoPeriodo periodo(LocalDate desde, LocalDate hasta, List<CobradoPeriodo.PorMedio> medios) {
		BigDecimal total = medios.stream().map(CobradoPeriodo.PorMedio::total).reduce(BigDecimal.ZERO.setScale(2),
				BigDecimal::add);
		long cantidad = medios.stream().mapToLong(CobradoPeriodo.PorMedio::cantidad).sum();
		BigDecimal efectivo = medios.stream().filter(m -> m.medio() == MedioPago.EFECTIVO)
				.map(CobradoPeriodo.PorMedio::total).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
		long pagosEfectivo = medios.stream().filter(m -> m.medio() == MedioPago.EFECTIVO)
				.mapToLong(CobradoPeriodo.PorMedio::cantidad).sum();
		return new CobradoPeriodo(desde, hasta, total, cantidad, efectivo, pagosEfectivo, medios, List.of());
	}
}
