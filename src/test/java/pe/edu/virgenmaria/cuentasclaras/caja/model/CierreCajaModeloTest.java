package pe.edu.virgenmaria.cuentasclaras.caja.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas puras del cierre ciego: conteo, reconteo, cierre, reapertura, conteo por denominaciones y depósito. */
class CierreCajaModeloTest {

	private static final BigDecimal ESPERADO = new BigDecimal("450.00");

	private static CajaDiaria caja() {
		return CajaDiaria.abrir("caja", LocalDate.of(2026, 10, 2), BigDecimal.ZERO);
	}

	private static ResumenCaja libro() {
		return new ResumenCaja(new BigDecimal("0.00"), ESPERADO, 1, 0, new BigDecimal("0.00"));
	}

	@Test
	void primerConteoQueCoincideNoPideReconteo() {
		CajaDiaria caja = caja();
		assertThat(caja.registrarConteo(new BigDecimal("450.0"), ESPERADO)).isEqualTo(ResultadoConteo.COINCIDE);
		assertThat(caja.getConteos()).isEqualTo(1);
		assertThat(caja.esperaReconteo()).isTrue();
	}

	@Test
	void unSoloReconteoYNoHayTercero() {
		CajaDiaria caja = caja();
		assertThat(caja.registrarConteo(new BigDecimal("400.00"), ESPERADO)).isEqualTo(ResultadoConteo.RECONTAR);
		assertThat(caja.getPrimerConteo()).isEqualByComparingTo("400.00");
		assertThat(caja.registrarConteo(new BigDecimal("450.00"), ESPERADO)).isEqualTo(ResultadoConteo.FINAL);
		// El primer conteo no se reescribe.
		assertThat(caja.getPrimerConteo()).isEqualByComparingTo("400.00");
		assertThatThrownBy(() -> caja.registrarConteo(new BigDecimal("450.00"), ESPERADO))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cierreCalculaEsperadoYDiferenciaYExigeExplicacionSiHayDiferencia() {
		CajaDiaria caja = caja();
		caja.registrarConteo(new BigDecimal("400.00"), ESPERADO);
		assertThatThrownBy(() -> CierreCaja.registrar(caja, libro(), new BigDecimal("400.00"), new BigDecimal("400.00"),
				null, null)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("explica");
		CierreCaja cierre = CierreCaja.registrar(caja, libro(), new BigDecimal("400.00"), new BigDecimal("400.00"), null,
				"Faltan cincuenta soles del cajón");
		assertThat(cierre.getEsperado()).isEqualByComparingTo("450.00");
		assertThat(cierre.getDiferencia()).isEqualByComparingTo("-50.00");
		assertThat(cierre.getNumero()).isEqualTo(1);
		assertThat(cierre.conDiferencia()).isTrue();
		assertThat(cierre.huboReconteo()).isTrue();
		assertThat(cierre.getEstado()).isEqualTo(EstadoCierre.POR_REVISAR);
		caja.cerrar(cierre);
		assertThat(caja.getEstado()).isEqualTo(EstadoCaja.CERRADA);
		assertThat(caja.getCierres()).isEqualTo(1);
		assertThat(caja.aceptaEfectivo()).isFalse();
		assertThatThrownBy(() -> caja.registrarConteo(ESPERADO, ESPERADO)).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void conteoConCentimosQueNoSonDecimosEsRechazado() {
		CajaDiaria caja = caja();
		caja.registrarConteo(new BigDecimal("450.05"), ESPERADO);
		assertThatThrownBy(() -> CierreCaja.registrar(caja, libro(), new BigDecimal("450.05"), new BigDecimal("450.05"),
				null, "Monedas de cinco céntimos")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("múltiplo de S/ 0.10");
	}

	@Test
	void reaperturaReiniciaElConteoPeroNoLosCierres() {
		CajaDiaria caja = caja();
		caja.registrarConteo(ESPERADO, ESPERADO);
		caja.cerrar(CierreCaja.registrar(caja, libro(), ESPERADO, ESPERADO, null, null));
		caja.reabrir(7L);
		assertThat(caja.getEstado()).isEqualTo(EstadoCaja.ABIERTA);
		assertThat(caja.getConteos()).isZero();
		assertThat(caja.getPrimerConteo()).isNull();
		assertThat(caja.getCierres()).isEqualTo(1);
		caja.registrarConteo(ESPERADO, ESPERADO);
		assertThat(CierreCaja.registrar(caja, libro(), ESPERADO, ESPERADO, null, null).getNumero()).isEqualTo(2);
		assertThatThrownBy(() -> caja.reabrir(8L)).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void conteoPorDenominacionesLoSumaElServidor() {
		Map<Denominacion, Integer> piezas = new EnumMap<>(Denominacion.class);
		piezas.put(Denominacion.B200, 2);
		piezas.put(Denominacion.B20, 2);
		piezas.put(Denominacion.M010, 3);
		ConteoEfectivo conteo = ConteoEfectivo.de(null, piezas);
		assertThat(conteo.total()).isEqualByComparingTo("440.30");
		assertThat(conteo.denominaciones()).isEqualTo("B200×2, B20×2, M010×3");
		assertThat(ConteoEfectivo.de(new BigDecimal("440.30"), piezas).total()).isEqualByComparingTo("440.30");
		assertThatThrownBy(() -> ConteoEfectivo.de(new BigDecimal("450.00"), piezas))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es la suma");
		piezas.put(Denominacion.B100, -1);
		assertThatThrownBy(() -> ConteoEfectivo.de(null, piezas)).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> ConteoEfectivo.de(null, null)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Escribe cuánto efectivo contaste");
	}

	@Test
	void depositoSoloDeCajaCerradaYDistintoExigeExplicacion() {
		CajaDiaria caja = caja();
		assertThatThrownBy(() -> DepositoCaja.registrar(caja, "BCP", "OP-1234", LocalDate.of(2026, 10, 2), ESPERADO,
				ESPERADO, null)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Primero cierra");
		caja.registrarConteo(ESPERADO, ESPERADO);
		caja.cerrar(CierreCaja.registrar(caja, libro(), ESPERADO, ESPERADO, null, null));
		assertThatThrownBy(() -> DepositoCaja.registrar(caja, "BCP", "OP-1234", LocalDate.of(2026, 10, 2),
				new BigDecimal("400.00"), ESPERADO, null)).isInstanceOf(ReglaNegocioException.class);
		DepositoCaja deposito = DepositoCaja.registrar(caja, "BCP", "op 1234", LocalDate.of(2026, 10, 2),
				new BigDecimal("400.00"), ESPERADO, "Se quedó sencillo por indicación");
		assertThat(deposito.distinto()).isTrue();
		assertThat(deposito.getNumeroOperacion()).isEqualTo("OP1234");
	}
}
