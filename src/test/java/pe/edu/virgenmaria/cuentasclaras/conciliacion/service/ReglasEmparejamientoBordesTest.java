package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ObjetoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.MovimientoAbierto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.ObjetoAbierto;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.Parametros;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.Propuesta;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ReglasEmparejamiento.Resultado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA del sprint 4: bordes de las reglas de emparejamiento (sección 10.4) que las pruebas del desarrollador no fijan:
 * montos al céntimo, límites exactos de ventana y de tolerancia (días hábiles y monto), orden de las reglas, empates y
 * fin de mes. Varias de estas pruebas matan mutaciones que sobrevivían a {@code ReglasEmparejamientoTest}.
 */
class ReglasEmparejamientoBordesTest {

	/** Viernes 02/10/2026. */
	private static final LocalDate VIERNES = LocalDate.of(2026, 10, 2);

	private static final Parametros PARAMETROS = new Parametros(2, BigDecimal.ZERO, "", Set.of());

	private static MovimientoAbierto abono(long id, LocalDate fecha, String monto, String operacion, String glosa) {
		return new MovimientoAbierto(id, fecha, TipoMovimiento.ABONO, new BigDecimal(monto), operacion, glosa, null);
	}

	private static ObjetoAbierto pago(long id, LocalDate fecha, String monto, String operacion) {
		LocalDate[] v = ReglasEmparejamiento.ventanaPago(fecha);
		return new ObjetoAbierto(ObjetoPartida.PAGO, id, fecha, new BigDecimal(monto), operacion, null, v[0], v[1],
				"Yape " + id, "caja");
	}

	private static ObjetoAbierto deposito(long id, LocalDate fecha, String monto, String operacion) {
		LocalDate[] v = ReglasEmparejamiento.ventanaDeposito(fecha);
		return new ObjetoAbierto(ObjetoPartida.DEPOSITO, id, fecha, new BigDecimal(monto), operacion, null, v[0], v[1],
				"Depósito " + id, "caja");
	}

	private static ObjetoAbierto liquidacion(long id, LocalDate abono, String neto, String referencia) {
		LocalDate[] v = ReglasEmparejamiento.ventanaLiquidacion(abono);
		return new ObjetoAbierto(ObjetoPartida.LIQUIDACION, id, abono, new BigDecimal(neto), null, referencia, v[0], v[1],
				"Liquidación " + referencia, null);
	}

	private static ObjetoAbierto lote(long id, LocalDate proceso, String total) {
		LocalDate[] v = ReglasEmparejamiento.ventanaRecaudacion(proceso);
		return new ObjetoAbierto(ObjetoPartida.LOTE_RECAUDACION, id, proceso, new BigDecimal(total), null, null, v[0], v[1],
				"Lote " + id, "administracion");
	}

	private static List<Propuesta> proponer(List<MovimientoAbierto> movimientos, List<ObjetoAbierto> objetos,
			Parametros p) {
		return ReglasEmparejamiento.proponer(movimientos, objetos, p).propuestas();
	}

	// ------------------------------------------------------------------ montos al céntimo

	@Test
	void debeNegarLaExactaSiElMontoDifiereEnUnCentimoAunqueLaOperacionCoincida() {
		assertThat(proponer(List.of(abono(1, VIERNES, "450.01", "YP1234", "YAPE")),
				List.of(pago(10, VIERNES, "450.00", "YP1234")), PARAMETROS)).isEmpty();
	}

	@Test
	void debeNegarLaExactaSiElBancoMuestraMenosQueLoRegistrado() {
		assertThat(proponer(List.of(abono(1, VIERNES, "449.99", "YP1234", "YAPE")),
				List.of(pago(10, VIERNES, "450.00", "YP1234")), PARAMETROS)).isEmpty();
	}

	@Test
	void debeTratarComoIgualesMontosConDistintaEscala() {
		MovimientoAbierto sinDecimales = new MovimientoAbierto(1L, VIERNES, TipoMovimiento.ABONO, new BigDecimal("450"),
				"YP1234", "YAPE", null);

		assertThat(proponer(List.of(sinDecimales), List.of(pago(10, VIERNES, "450.00", "YP1234")), PARAMETROS))
				.extracting(Propuesta::regla).containsExactly(ReglaPartida.EXACTA);
	}

	@Test
	void debeNoAplicarLaToleranciaDeLiquidacionAUnPagoDeCaja() {
		Parametros conTolerancia = new Parametros(2, new BigDecimal("1.00"), "", Set.of());

		assertThat(proponer(List.of(abono(1, VIERNES, "449.99", null, "TRANSF")),
				List.of(pago(10, VIERNES, "450.00", "TR1")), conTolerancia)).isEmpty();
	}

	@Test
	void debeAdmitirLaDiferenciaDeLiquidacionExactamenteIgualALaTolerancia() {
		Parametros conTolerancia = new Parametros(2, new BigDecimal("0.50"), "", Set.of());

		assertThat(proponer(List.of(abono(1, VIERNES, "867.74", null, "ABONO PASARELA")),
				List.of(liquidacion(20, VIERNES, "868.24", "77-AB12")), conTolerancia)).singleElement().satisfies(p -> {
					assertThat(p.regla()).isEqualTo(ReglaPartida.SUGERIDA);
					assertThat(p.avisos()).containsExactly("Diferencia de monto: -0.50");
				});
	}

	@Test
	void debeRechazarLaDiferenciaDeLiquidacionUnCentimoSobreLaTolerancia() {
		Parametros conTolerancia = new Parametros(2, new BigDecimal("0.50"), "", Set.of());

		assertThat(proponer(List.of(abono(1, VIERNES, "867.73", null, "ABONO PASARELA")),
				List.of(liquidacion(20, VIERNES, "868.24", "77-AB12")), conTolerancia)).isEmpty();
	}

	@Test
	void debeAvisarDiferenciaPositivaCuandoElBancoAbonaMasQueLaLiquidacion() {
		Parametros conTolerancia = new Parametros(2, new BigDecimal("0.10"), "", Set.of());

		assertThat(proponer(List.of(abono(1, VIERNES, "868.34", null, "ABONO PASARELA")),
				List.of(liquidacion(20, VIERNES, "868.24", "77-AB12")), conTolerancia)).singleElement()
				.satisfies(p -> assertThat(p.avisos()).containsExactly("Diferencia de monto: 0.10"));
	}

	// ------------------------------------------------------------------ ventanas y días hábiles

	@Test
	void debeNegarLaExactaSiElAbonoLlegaAntesDelPagoRegistrado() {
		// El banco muestra el jueves un Yape que la cajera registró el viernes: fuera de su ventana, solo SUGERIDA.
		assertThat(proponer(List.of(abono(1, VIERNES.minusDays(1), "450.00", "YP1234", "YAPE")),
				List.of(pago(10, VIERNES, "450.00", "YP1234")), PARAMETROS)).extracting(Propuesta::regla)
				.containsExactly(ReglaPartida.SUGERIDA);
	}

	@Test
	void debeSugerirJustoEnElLimiteDeDiasHabilesDeTolerancia() {
		// Viernes → martes: 2 días hábiles (lunes y martes), igual a la tolerancia.
		assertThat(proponer(List.of(abono(1, VIERNES.plusDays(4), "380.00", null, "TRANSF")),
				List.of(pago(10, VIERNES, "380.00", "TR555")), PARAMETROS)).extracting(Propuesta::regla)
				.containsExactly(ReglaPartida.SUGERIDA);
	}

	@Test
	void debeNoSugerirUnDiaHabilDespuesDelLimiteDeTolerancia() {
		// Viernes → miércoles: 3 días hábiles.
		assertThat(proponer(List.of(abono(1, VIERNES.plusDays(5), "380.00", null, "TRANSF")),
				List.of(pago(10, VIERNES, "380.00", "TR555")), PARAMETROS)).isEmpty();
	}

	@Test
	void debeContarElFinDeSemanaComoCeroDiasHabilesParaLaSugerida() {
		Parametros sinTolerancia = new Parametros(0, BigDecimal.ZERO, "", Set.of());

		assertThat(proponer(List.of(abono(1, VIERNES.plusDays(1), "380.00", null, "TRANSF")),
				List.of(pago(10, VIERNES, "380.00", "TR555")), sinTolerancia)).extracting(Propuesta::regla)
				.containsExactly(ReglaPartida.SUGERIDA);
	}

	@ParameterizedTest
	@CsvSource({ "2026-10-30, 2026-11-02, 1", "2026-12-31, 2027-01-04, 2", "2026-02-27, 2026-03-02, 1",
			"2026-10-31, 2026-11-01, 0", "2026-10-05, 2026-10-09, 4", "2026-10-09, 2026-10-05, 4" })
	void debeContarDiasHabilesAlCruzarFinDeMesYDeAnio(LocalDate a, LocalDate b, int dias) {
		assertThat(ReglasEmparejamiento.diasHabilesEntre(a, b)).isEqualTo(dias);
	}

	@Test
	void debeAbrirLaVentanaDelDepositoDelViernesHastaElLunes() {
		assertThat(ReglasEmparejamiento.ventanaDeposito(VIERNES)).containsExactly(VIERNES, VIERNES.plusDays(3));
	}

	@Test
	void debeAbrirLaVentanaDeLaRecaudacionDelUltimoDiaDelMesHastaElDiaHabilSiguiente() {
		LocalDate viernes30 = LocalDate.of(2026, 10, 30);

		assertThat(ReglasEmparejamiento.ventanaRecaudacion(viernes30)).containsExactly(viernes30,
				LocalDate.of(2026, 11, 2));
	}

	@Test
	void debeAbrirLaVentanaDeLaLiquidacionDeUnDiaHabilAntesATresDespues() {
		LocalDate lunes = LocalDate.of(2026, 10, 5);

		assertThat(ReglasEmparejamiento.ventanaLiquidacion(lunes)).containsExactly(VIERNES, LocalDate.of(2026, 10, 8));
	}

	@Test
	void debeAbrirLaVentanaDelPagoTresDiasCalendarioYNoCuatro() {
		assertThat(ReglasEmparejamiento.ventanaPago(VIERNES)).containsExactly(VIERNES, VIERNES.plusDays(3));
	}

	@Test
	void debeEmparejarExactoUnDepositoVistoElDiaHabilSiguiente() {
		assertThat(proponer(List.of(abono(1, VIERNES.plusDays(3), "1200.00", "DEP777", "DEPOSITO EFECTIVO")),
				List.of(deposito(50, VIERNES, "1200.00", "DEP777")), PARAMETROS)).extracting(Propuesta::regla)
				.containsExactly(ReglaPartida.EXACTA);
	}

	@Test
	void debeNegarLaExactaDeUnDepositoVistoDosDiasHabilesDespues() {
		assertThat(proponer(List.of(abono(1, VIERNES.plusDays(4), "1200.00", "DEP777", "DEPOSITO EFECTIVO")),
				List.of(deposito(50, VIERNES, "1200.00", "DEP777")), PARAMETROS)).extracting(Propuesta::regla)
				.containsExactly(ReglaPartida.SUGERIDA);
	}

	// ------------------------------------------------------------------ orden de las reglas, empates

	@Test
	void debeAplicarLaExactaPorOperacionAntesQueLaSugerida() {
		// m1 trae la operación de o10; m2 no trae operación. Si la SUGERIDA corriera primero, m1 y m2 competirían.
		List<Propuesta> propuestas = proponer(List.of(abono(1, VIERNES, "450.00", "YP1234", "YAPE"),
				abono(2, VIERNES, "450.00", null, "TRANSF")), List.of(pago(10, VIERNES, "450.00", "YP1234"),
						pago(11, VIERNES, "450.00", null)), PARAMETROS);

		assertThat(propuestas).extracting(p -> p.movimiento().id() + ">" + p.objeto().id() + ":" + p.regla())
				.containsExactlyInAnyOrder("1>10:EXACTA", "2>11:SUGERIDA");
	}

	@Test
	void debeNoProponerExactaSiDosObjetosTienenLaMismaOperacion() {
		// Un Yape y un depósito con la misma operación (uno de los dos es un error o un fraude): nadie es «exacto».
		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "450.00", "OP555", "ABONO")),
				List.of(pago(10, VIERNES, "450.00", "OP555"), deposito(50, VIERNES, "450.00", "OP555")), PARAMETROS);

		assertThat(r.propuestas()).isEmpty();
		assertThat(r.posibles().get(1L)).extracting(ObjetoAbierto::id).containsExactlyInAnyOrder(10L, 50L);
	}

	@Test
	void debeDejarComoPosiblesAAmbosAbonosCuandoCompitenPorUnSoloObjeto() {
		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "450.00", null, "A"),
				abono(2, VIERNES, "450.00", null, "B")), List.of(pago(10, VIERNES, "450.00", "TR1")), PARAMETROS);

		assertThat(r.propuestas()).isEmpty();
		assertThat(r.posibles()).containsOnlyKeys(1L, 2L);
	}

	@Test
	void debeProponerElOtroObjetoCuandoElParDescartadoEraElUnico() {
		Parametros descartado = new Parametros(2, BigDecimal.ZERO, "", Set.of("1|PAGO:10"));

		assertThat(proponer(List.of(abono(1, VIERNES, "380.00", null, "TRANSF")),
				List.of(pago(10, VIERNES, "380.00", "TR555"), pago(11, VIERNES, "380.00", "TR556")), descartado))
				.singleElement().satisfies(p -> assertThat(p.objeto().id()).isEqualTo(11L));
	}

	@Test
	void debeAvisarOperacionDistintaSinRojoCuandoDifierenEnDosCaracteres() {
		assertThat(proponer(List.of(abono(1, VIERNES, "450.00", "YP123456", "YAPE")),
				List.of(pago(10, VIERNES, "450.00", "YP123465")), PARAMETROS)).singleElement().satisfies(p -> {
					assertThat(p.parecido()).isFalse();
					assertThat(p.avisos()).containsExactly(ReglasEmparejamiento.OPERACION_DISTINTA);
				});
	}

	@Test
	void debeNoEmparejarUnCargoConUnPagoDeCajaDelMismoMonto() {
		MovimientoAbierto cargo = new MovimientoAbierto(1L, VIERNES, TipoMovimiento.CARGO, new BigDecimal("450.00"),
				"YP1234", "CARGO", null);

		assertThat(proponer(List.of(cargo), List.of(pago(10, VIERNES, "450.00", "YP1234")), PARAMETROS)).isEmpty();
	}

	// ------------------------------------------------------------------ EXACTA por referencia y por glosa

	@Test
	void debeAceptarUnaReferenciaDeLiquidacionDeCuatroCaracteres() {
		assertThat(proponer(List.of(abono(1, VIERNES, "868.24", null, "ABONO LIQ AB12")),
				List.of(liquidacion(20, VIERNES, "868.24", "AB12")), PARAMETROS)).extracting(Propuesta::regla)
				.containsExactly(ReglaPartida.EXACTA);
	}

	@Test
	void debeNoUsarComoReferenciaUnTextoDeTresCaracteres() {
		// «A12» aparecería por casualidad en cualquier glosa: no basta para una EXACTA (queda como SUGERIDA).
		assertThat(proponer(List.of(abono(1, VIERNES, "868.24", null, "ABONO LIQ A12")),
				List.of(liquidacion(20, VIERNES, "868.24", "A12")), PARAMETROS)).extracting(Propuesta::regla)
				.containsExactly(ReglaPartida.SUGERIDA);
	}

	@Test
	void debeEncontrarLaReferenciaEnLaColumnaReferenciaAunqueLaGlosaNoLaTenga() {
		MovimientoAbierto conReferencia = new MovimientoAbierto(1L, VIERNES, TipoMovimiento.ABONO,
				new BigDecimal("868.24"), null, "ABONO PASARELA", "liq-77/ab12");

		assertThat(proponer(List.of(conReferencia), List.of(liquidacion(20, VIERNES, "868.24", "77-AB12")), PARAMETROS))
				.extracting(Propuesta::regla).containsExactly(ReglaPartida.EXACTA);
	}

	@Test
	void debeNoEmparejarPorGlosaUnLoteDeOtroMonto() {
		Parametros conPatron = new Parametros(2, BigDecimal.ZERO, "RECAUD", Set.of());

		assertThat(proponer(List.of(abono(1, VIERNES, "1755.01", null, "ABONO RECAUD COD ALUMNO")),
				List.of(lote(30, VIERNES, "1755.00")), conPatron)).isEmpty();
	}

	@Test
	void debeNoEmparejarPorGlosaUnAbonoMenorQueElTotalDelLote() {
		Parametros conPatron = new Parametros(2, BigDecimal.ZERO, "RECAUD", Set.of());

		assertThat(proponer(List.of(abono(1, VIERNES, "1754.99", null, "ABONO RECAUD COD ALUMNO")),
				List.of(lote(30, VIERNES, "1755.00")), conPatron)).isEmpty();
	}

	@Test
	void debeNoEmparejarPorReferenciaUnaLiquidacionDeMayorNeto() {
		assertThat(proponer(List.of(abono(1, VIERNES, "868.23", null, "ABONO LIQ 77-AB12")),
				List.of(liquidacion(20, VIERNES, "868.24", "77-AB12")), PARAMETROS)).isEmpty();
	}

	@Test
	void debeNoEmparejarPorGlosaDosLotesDelMismoTotalEnLaVentana() {
		Parametros conPatron = new Parametros(2, BigDecimal.ZERO, "RECAUD", Set.of());

		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "900.00", null, "ABONO RECAUD")),
				List.of(lote(30, VIERNES, "900.00"), lote(31, VIERNES, "900.00")), conPatron);

		assertThat(r.propuestas()).isEmpty();
		assertThat(r.posibles().get(1L)).hasSize(2);
	}

	@Test
	void debeNoUsarLaReglaDeGlosaParaUnCargo() {
		Parametros conPatron = new Parametros(2, BigDecimal.ZERO, "RECAUD", Set.of());
		MovimientoAbierto cargo = new MovimientoAbierto(1L, VIERNES, TipoMovimiento.CARGO, new BigDecimal("900.00"), null,
				"COMISION RECAUD", null);

		assertThat(proponer(List.of(cargo), List.of(lote(30, VIERNES, "900.00")), conPatron)).isEmpty();
	}
}
