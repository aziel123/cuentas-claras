package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo pagado y lo descontado de una cuota son libros (sprint 3): la cuota solo refleja la suma y recalcula su estado.
 * Nunca queda pagada de más ni recibe pagos estando anulada.
 */
class CuotaLibroTest {

	@Test
	void nacePendienteSinPagosNiDescuentos() {
		Cuota cuota = CuotasDePrueba.pensionSetiembre("450.00");

		assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
		assertThat(cuota.getMontoPagado()).isEqualByComparingTo("0.00");
		assertThat(cuota.getMontoDescuento()).isEqualByComparingTo("0.00");
		assertThat(cuota.admiteCobro()).isTrue();
	}

	@Test
	void reflejarPagosRecalculaElEstado() {
		Cuota cuota = CuotasDePrueba.pensionSetiembre("450.00");

		cuota.reflejarPagos(new BigDecimal("100"));
		assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.PARCIAL);
		assertThat(cuota.saldo()).isEqualByComparingTo("350.00");
		assertThat(cuota.admiteCobro()).isTrue();

		cuota.reflejarPagos(new BigDecimal("450.00"));
		assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.PAGADA);
		assertThat(cuota.saldo()).isEqualByComparingTo("0.00");
		assertThat(cuota.admiteCobro()).isFalse();

		// Una reversión (tanda 2) devuelve el libro a cero: vuelve a PENDIENTE.
		cuota.reflejarPagos(BigDecimal.ZERO);
		assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
	}

	@Test
	void pagarMasDeLoQueSeDebeEsRechazado() {
		Cuota cuota = CuotasDePrueba.pensionSetiembre("450.00");

		assertThatThrownBy(() -> cuota.reflejarPagos(new BigDecimal("450.01")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("supera");
		assertThatThrownBy(() -> cuota.reflejarPagos(new BigDecimal("-1.00"))).isInstanceOf(ReglaNegocioException.class);
		assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
		assertThat(cuota.getMontoPagado()).isEqualByComparingTo("0.00");
	}

	@Test
	void descuentoDelCienPorCientoDejaLaCuotaExonerada() {
		Cuota cuota = CuotasDePrueba.pensionSetiembre("450.00");

		cuota.reflejarDescuentos(new BigDecimal("45.00"));
		assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
		assertThat(cuota.saldo()).isEqualByComparingTo("405.00");

		cuota.reflejarDescuentos(new BigDecimal("450.00"));
		assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.EXONERADA);
		assertThat(cuota.saldo()).isEqualByComparingTo("0.00");
		assertThat(cuota.admiteCobro()).isFalse();
	}

	@Test
	void descuentoYPagoJuntosNoSuperanElMonto() {
		Cuota cuota = CuotasDePrueba.pensionSetiembre("450.00");
		cuota.reflejarDescuentos(new BigDecimal("50.00"));
		cuota.reflejarPagos(new BigDecimal("400.00"));

		assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.PAGADA);
		assertThatThrownBy(() -> cuota.reflejarDescuentos(new BigDecimal("50.10")))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void cuotaAnuladaNoRecibePagos() {
		Cuota cuota = CuotasDePrueba.pensionSetiembre("450.00");
		cuota.anular("La familia se retiró antes de setiembre", "administracion", "director",
				LocalDateTime.of(2027, 8, 2, 9, 0), 5L);

		assertThat(cuota.admiteCobro()).isFalse();
		assertThatThrownBy(() -> cuota.reflejarPagos(new BigDecimal("450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("anulada");
	}

	@Test
	void cuotaConAnulacionPendienteNoAdmiteCobro() {
		Cuota cuota = CuotasDePrueba.pensionSetiembre("450.00");
		cuota.solicitarAnulacion("Se generó dos veces por error", "administracion");

		assertThat(cuota.admiteCobro()).isFalse();
	}
}
