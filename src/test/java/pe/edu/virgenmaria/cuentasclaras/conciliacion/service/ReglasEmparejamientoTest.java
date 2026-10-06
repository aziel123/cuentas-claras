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

/** Sprint 4, tanda 3: las reglas de emparejamiento (sección 10.4 del diseño), puras y en su orden. */
class ReglasEmparejamientoTest {

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

	@Test
	void mismaOperacionYMontoEsExacta() {
		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "450.00", "YP1234", "ABONO YAPE")),
				List.of(pago(10, VIERNES, "450.00", "YP1234"), pago(11, VIERNES, "450.00", "YP9999")), PARAMETROS);

		assertThat(r.propuestas()).singleElement().satisfies(p -> {
			assertThat(p.regla()).isEqualTo(ReglaPartida.EXACTA);
			assertThat(p.objeto().id()).isEqualTo(10L);
			assertThat(p.avisos()).isEmpty();
		});
	}

	/** Un Yape del viernes que el banco muestra el lunes (dentro de los 3 días) sigue siendo exacto. */
	@Test
	void exactaHastaTresDiasDespues() {
		assertThat(ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES.plusDays(3), "450.00", "YP1234", "YAPE")),
				List.of(pago(10, VIERNES, "450.00", "YP1234")), PARAMETROS).propuestas())
				.extracting(Propuesta::regla).containsExactly(ReglaPartida.EXACTA);
	}

	@Test
	void mismoMontoYFechaCercanaEsSugerida() {
		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES.plusDays(3), "380.00", null, "TRANSF")),
				List.of(pago(10, VIERNES, "380.00", "TR555")), PARAMETROS);

		assertThat(r.propuestas()).singleElement().satisfies(p -> {
			assertThat(p.regla()).isEqualTo(ReglaPartida.SUGERIDA);
			assertThat(p.parecido()).isFalse();
		});
	}

	@Test
	void operacionDistintaSeAvisa() {
		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "380.00", "TR777001", "TRANSF")),
				List.of(pago(10, VIERNES, "380.00", "TR555123")), PARAMETROS);

		assertThat(r.propuestas()).singleElement().satisfies(p -> {
			assertThat(p.regla()).isEqualTo(ReglaPartida.SUGERIDA);
			assertThat(p.avisos()).containsExactly(ReglasEmparejamiento.OPERACION_DISTINTA);
		});
	}

	/** C1: un número inventado a partir de uno real (difiere en un carácter) se marca en rojo. */
	@Test
	void numeroParecidoSeMarcaEnRojo() {
		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "450.00", "YP123456", "YAPE")),
				List.of(pago(10, VIERNES, "450.00", "YP123457")), PARAMETROS);

		assertThat(r.propuestas()).singleElement().satisfies(p -> {
			assertThat(p.regla()).isEqualTo(ReglaPartida.SUGERIDA);
			assertThat(p.parecido()).isTrue();
			assertThat(p.avisos()).containsExactly(ReglasEmparejamiento.NUMERO_PARECIDO);
		});
	}

	/** Dos objetos posibles para el mismo abono: no se propone nada; una persona elige entre los posibles. */
	@Test
	void dosCandidatosNoProponeNada() {
		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "450.00", null, "TRANSF")),
				List.of(pago(10, VIERNES, "450.00", "TR1"), pago(11, VIERNES, "450.00", "TR2")), PARAMETROS);

		assertThat(r.propuestas()).isEmpty();
		assertThat(r.posibles().get(1L)).extracting(ObjetoAbierto::id).containsExactly(10L, 11L);
	}

	/** Y al revés: dos abonos iguales para un solo objeto tampoco (candidato único en ambos sentidos). */
	@Test
	void dosAbonosParaUnObjetoNoProponeNada() {
		assertThat(ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "450.00", null, "A"),
				abono(2, VIERNES, "450.00", null, "B")), List.of(pago(10, VIERNES, "450.00", "TR1")), PARAMETROS)
				.propuestas()).isEmpty();
	}

	@Test
	void liquidacionPorReferencia() {
		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "868.24", null, "ABONO CULQI LIQ 77-AB12")),
				List.of(liquidacion(20, VIERNES, "868.24", "77-AB12"), liquidacion(21, VIERNES, "868.24", "77-ZZ99")),
				PARAMETROS);

		assertThat(r.propuestas()).singleElement().satisfies(p -> {
			assertThat(p.regla()).isEqualTo(ReglaPartida.EXACTA);
			assertThat(p.objeto().id()).isEqualTo(20L);
		});
	}

	/** Sin la referencia en la glosa, una liquidación solo sale como sugerida, y con tolerancia si está configurada. */
	@Test
	void liquidacionSinReferenciaConToleranciaEsSugerida() {
		Parametros conTolerancia = new Parametros(2, new BigDecimal("0.05"), "", Set.of());

		assertThat(ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "868.20", null, "ABONO PASARELA")),
				List.of(liquidacion(20, VIERNES, "868.24", "77-AB12")), conTolerancia).propuestas())
				.singleElement().satisfies(p -> {
					assertThat(p.regla()).isEqualTo(ReglaPartida.SUGERIDA);
					assertThat(p.avisos()).anyMatch(a -> a.contains("-0.04"));
				});
		assertThat(ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "868.20", null, "ABONO PASARELA")),
				List.of(liquidacion(20, VIERNES, "868.24", "77-AB12")), PARAMETROS).propuestas()).isEmpty();
	}

	@Test
	void loteConPatronDeGlosa() {
		Parametros conPatron = new Parametros(2, BigDecimal.ZERO, "RECAUD", Set.of());
		MovimientoAbierto abono = abono(1, VIERNES, "1755.00", null, "ABONO RECAUD COD ALUMNO");

		assertThat(ReglasEmparejamiento.proponer(List.of(abono), List.of(lote(30, VIERNES, "1755.00")), conPatron)
				.propuestas()).extracting(Propuesta::regla).containsExactly(ReglaPartida.EXACTA);
		// Sin patrón configurado, el mismo abono es solo una sugerida.
		assertThat(ReglasEmparejamiento.proponer(List.of(abono), List.of(lote(30, VIERNES, "1755.00")), PARAMETROS)
				.propuestas()).extracting(Propuesta::regla).containsExactly(ReglaPartida.SUGERIDA);
	}

	/**
	 * Un pago en línea no es un objeto del extracto (lo cubre su liquidación): aunque tenga la misma operación que un
	 * abono, si no se lo pasa como objeto, no se empareja (ObjetosConciliables solo da los de ventanilla).
	 */
	@Test
	void pagoPasarelaNoSeEmparejaDirecto() {
		assertThat(ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "450.00", "SIM123456", "ABONO")),
				List.of(liquidacion(20, VIERNES.plusDays(3), "433.55", "SIMLIQ20261002")), PARAMETROS).propuestas())
				.isEmpty();
	}

	@ParameterizedTest
	@CsvSource({ "4, false", "3, true", "-1, false" })
	void fueraDeVentanaNoEsExacta(int dias, boolean exacta) {
		Resultado r = ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES.plusDays(dias), "450.00", "YP1234", "YAPE")),
				List.of(pago(10, VIERNES, "450.00", "YP1234")), new Parametros(0, BigDecimal.ZERO, "", Set.of()));

		assertThat(r.propuestas().stream().anyMatch(p -> p.regla() == ReglaPartida.EXACTA)).isEqualTo(exacta);
	}

	/** Fuera de los días hábiles de tolerancia, ni siquiera se sugiere. */
	@Test
	void fueraDeVentanaNoSeEmpareja() {
		assertThat(ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES.plusDays(10), "380.00", null, "TRANSF")),
				List.of(pago(10, VIERNES, "380.00", "TR555")), PARAMETROS).propuestas()).isEmpty();
	}

	/** Un par que una persona descartó («No es») no se vuelve a proponer. */
	@Test
	void parDescartadoNoSeVuelveAProponer() {
		Parametros descartado = new Parametros(2, BigDecimal.ZERO, "", Set.of("1|PAGO:10"));

		assertThat(ReglasEmparejamiento.proponer(List.of(abono(1, VIERNES, "380.00", null, "TRANSF")),
				List.of(pago(10, VIERNES, "380.00", "TR555")), descartado).propuestas()).isEmpty();
	}

	/** Un cargo solo se empareja con un reembolso; un abono, nunca con un reembolso. */
	@Test
	void cargoSoloConReembolso() {
		LocalDate[] v = ReglasEmparejamiento.ventanaReembolso(VIERNES);
		ObjetoAbierto reembolso = new ObjetoAbierto(ObjetoPartida.REEMBOLSO, 40L, VIERNES, new BigDecimal("450.00"),
				"DEV123", null, v[0], v[1], "Devolución", "administracion");
		MovimientoAbierto cargo = new MovimientoAbierto(1L, VIERNES, TipoMovimiento.CARGO, new BigDecimal("450.00"), "DEV123",
				"TRANSF A TERCEROS", null);

		assertThat(ReglasEmparejamiento.proponer(List.of(cargo), List.of(reembolso, pago(10, VIERNES, "450.00", "DEV123")),
				PARAMETROS).propuestas()).singleElement().satisfies(p -> {
					assertThat(p.regla()).isEqualTo(ReglaPartida.EXACTA);
					assertThat(p.objeto().tipo()).isEqualTo(ObjetoPartida.REEMBOLSO);
				});
		assertThat(ReglasEmparejamiento.proponer(List.of(abono(2, VIERNES, "450.00", "DEV123", "ABONO")), List.of(reembolso),
				PARAMETROS).propuestas()).isEmpty();
	}

	/** En la vista previa los movimientos no tienen id: dos movimientos iguales en todo siguen siendo dos. */
	@Test
	void movimientosSinIdIgualesNoSeConfunden() {
		MovimientoAbierto a = new MovimientoAbierto(null, VIERNES, TipoMovimiento.ABONO, new BigDecimal("450.00"), "YP1",
				"YAPE", null);
		MovimientoAbierto b = new MovimientoAbierto(null, VIERNES, TipoMovimiento.ABONO, new BigDecimal("450.00"), "YP1",
				"YAPE", null);

		assertThat(ReglasEmparejamiento.proponer(List.of(a, b), List.of(pago(10, VIERNES, "450.00", "YP1")), PARAMETROS)
				.propuestas()).hasSize(1);
	}

	@ParameterizedTest
	@CsvSource({ "2026-10-02, 2026-10-02, 0", "2026-10-02, 2026-10-05, 1", "2026-10-05, 2026-10-02, 1",
			"2026-10-02, 2026-10-06, 2", "2026-10-03, 2026-10-04, 0" })
	void diasHabilesEntre(LocalDate a, LocalDate b, int dias) {
		assertThat(ReglasEmparejamiento.diasHabilesEntre(a, b)).isEqualTo(dias);
	}
}
