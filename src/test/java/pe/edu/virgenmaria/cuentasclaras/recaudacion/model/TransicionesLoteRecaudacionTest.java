package pe.edu.virgenmaria.cuentasclaras.recaudacion.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 4: la máquina de estados del lote de recaudación (sección 5): CARGADO → CONFIRMADO → APLICADO;
 * CARGADO → RECHAZADO (intentos a ciegas) o DESCARTADO. Ninguna transición inválida se acepta. Puro.
 */
class TransicionesLoteRecaudacionTest {

	private static final LocalDate DIA = LocalDate.of(2026, 10, 1);

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 9, 0);

	private static final String MOTIVO = "Subí el archivo del día equivocado";

	private LoteRecaudacion lote;

	@BeforeEach
	void registrar() {
		lote = LoteRecaudacion.registrar(1L, "a".repeat(64), BancoRecaudacion.GENERICO, "GENERICO_CSV", DIA, DIA, DIA, 2,
				new BigDecimal("800.00"), new BigDecimal("800.00"));
	}

	@Test
	void debeNacerCargadoConElShaVigente() {
		assertThat(lote.getEstado()).isEqualTo(EstadoLote.CARGADO);
		assertThat(lote.getShaVigente()).isEqualTo("a".repeat(64));
		assertThat(lote.getMontoAplicado()).isEqualByComparingTo("0.00");
	}

	@Test
	void debeRechazarUnTotalDelPieQueNoEsLaSumaDeLasLineas() {
		assertThatThrownBy(() -> LoteRecaudacion.registrar(1L, "b".repeat(64), BancoRecaudacion.GENERICO, "GENERICO_CSV",
				DIA, DIA, DIA, 2, new BigDecimal("800.00"), new BigDecimal("800.01")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void debeRechazarUnLoteSinLineasOConFechasAlReves() {
		assertThatThrownBy(() -> LoteRecaudacion.registrar(1L, "b".repeat(64), BancoRecaudacion.GENERICO, "GENERICO_CSV",
				DIA, DIA, DIA, 0, new BigDecimal("800.00"), null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> LoteRecaudacion.registrar(1L, "b".repeat(64), BancoRecaudacion.GENERICO, "GENERICO_CSV",
				DIA, DIA.plusDays(1), DIA, 1, new BigDecimal("800.00"), null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void debeConfirmarConElTotalExactoAunqueVengaConOtraEscala() {
		lote.confirmar("promotor", new BigDecimal("800.0"), AHORA);

		assertThat(lote.getEstado()).isEqualTo(EstadoLote.CONFIRMADO);
		assertThat(lote.getTotalCiego()).isEqualByComparingTo("800.00");
	}

	@Test
	void debeRechazarConfirmarConUnCentimoDeDiferencia() {
		assertThatThrownBy(() -> lote.confirmar("promotor", new BigDecimal("799.99"), AHORA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(lote.getEstado()).isEqualTo(EstadoLote.CARGADO);
	}

	@Test
	void debeQuedarRechazadoAlLlegarAlMaximoDeIntentosYSoltarElSha() {
		assertThat(lote.intentoFallido(2, "promotor", AHORA)).isFalse();
		assertThat(lote.intentosRestantes(2)).isEqualTo(1);

		assertThat(lote.intentoFallido(2, "director", AHORA)).isTrue();

		assertThat(lote.getEstado()).isEqualTo(EstadoLote.RECHAZADO);
		assertThat(lote.getShaVigente()).isNull();
		assertThat(lote.intentosRestantes(2)).isZero();
	}

	@Test
	void debeImpedirConfirmarUnLoteRechazado() {
		lote.intentoFallido(1, "promotor", AHORA);

		assertThatThrownBy(() -> lote.confirmar("director", new BigDecimal("800.00"), AHORA))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void debeImpedirDescartarUnLoteConfirmado() {
		lote.confirmar("promotor", new BigDecimal("800.00"), AHORA);

		assertThatThrownBy(() -> lote.descartar("administracion", MOTIVO, AHORA))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(lote.getEstado()).isEqualTo(EstadoLote.CONFIRMADO);
	}

	@Test
	void debeImpedirConfirmarUnLoteDescartado() {
		lote.descartar("administracion", MOTIVO, AHORA);

		assertThatThrownBy(() -> lote.confirmar("promotor", new BigDecimal("800.00"), AHORA))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(lote.getShaVigente()).isNull();
	}

	@Test
	void debeExigirMotivoParaDescartar() {
		assertThatThrownBy(() -> lote.descartar("administracion", "corto", AHORA))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("motivo");
	}

	@Test
	void debeImpedirMarcarAplicadoUnLoteSinConfirmar() {
		assertThatThrownBy(() -> lote.marcarAplicado(2, 0, new BigDecimal("800.00"), new BigDecimal("0.00"), AHORA))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void debeExigirQueAplicadoMasExcepcionSumeElTotalAlCentimo() {
		lote.confirmar("promotor", new BigDecimal("800.00"), AHORA);

		assertThatThrownBy(() -> lote.marcarAplicado(1, 1, new BigDecimal("450.00"), new BigDecimal("349.99"), AHORA))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> lote.marcarAplicado(1, 0, new BigDecimal("450.00"), new BigDecimal("350.00"), AHORA))
				.isInstanceOf(IllegalStateException.class);

		lote.marcarAplicado(1, 1, new BigDecimal("450.00"), new BigDecimal("350.00"), AHORA);
		assertThat(lote.getEstado()).isEqualTo(EstadoLote.APLICADO);
	}

	@Test
	void debeImpedirAplicarDosVecesUnLote() {
		lote.confirmar("promotor", new BigDecimal("800.00"), AHORA);
		lote.marcarAplicado(2, 0, new BigDecimal("800.00"), new BigDecimal("0.00"), AHORA);

		assertThatThrownBy(() -> lote.marcarAplicado(2, 0, new BigDecimal("800.00"), new BigDecimal("0.00"), AHORA))
				.isInstanceOf(IllegalStateException.class);
	}
}
