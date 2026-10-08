package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.cobranza.model.ModalidadDescuento.MONTO;
import static pe.edu.virgenmaria.cuentasclaras.cobranza.model.ModalidadDescuento.PORCENTAJE;

/** Descuentos (diseño, sección 10.5): sobre el monto original, redondeando a favor del apoderado en décimos. */
class CalculadoraDescuentoTest {

	@ParameterizedTest(name = "{1} % de {0} → descuento {2}")
	@CsvSource({ "437.50, 10, 43.80", "450.00, 10, 45.00", "450.00, 50, 225.00", "380.00, 15, 57.00",
			"399.99, 89.99, 359.99", "350.00, 33.33, 116.70" })
	void diezPorCientoDe437_50Es43_80(String monto, String porcentaje, String descuento) {
		BigDecimal ajuste = CalculadoraDescuento.ajuste(new BigDecimal(monto), PORCENTAJE, new BigDecimal(porcentaje));

		assertThat(ajuste).isEqualByComparingTo(descuento);
		// Lo que queda por pagar se puede pagar exacto en efectivo.
		assertThat(Dinero.enDecimos(new BigDecimal(monto).subtract(ajuste))).isTrue();
	}

	@ParameterizedTest
	@CsvSource({ "399.99, 89.99", "437.51, 12.34", "0.01, 0.01", "99999.99, 99.99", "123.45, 66.67", "1.11, 33.33" })
	void porcentajeConDecimalesNoLanzaArithmeticException(String monto, String porcentaje) {
		assertThatCode(() -> CalculadoraDescuento.ajuste(new BigDecimal(monto), PORCENTAJE, new BigDecimal(porcentaje)))
				.doesNotThrowAnyException();
		BigDecimal ajuste = CalculadoraDescuento.ajuste(new BigDecimal(monto), PORCENTAJE, new BigDecimal(porcentaje));
		assertThat(ajuste.scale()).isEqualTo(2);
		assertThat(ajuste.compareTo(new BigDecimal(monto))).isLessThanOrEqualTo(0);
	}

	@Test
	void cienPorCientoDejaLaCuotaExonerada() {
		Cuota cuota = CuotasDePrueba.pensionSetiembre("450.00");
		BigDecimal ajuste = CalculadoraDescuento.ajuste(cuota.getMonto(), PORCENTAJE, new BigDecimal("100"));

		assertThat(ajuste).isEqualByComparingTo("450.00");
		cuota.reflejarDescuentos(ajuste);
		assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.EXONERADA);
		assertThat(cuota.saldo()).isEqualByComparingTo("0.00");
		assertThat(cuota.admiteCobro()).isFalse();
	}

	@Test
	void montoFijoMayorQueElSaldoEsRechazado() {
		assertThat(CalculadoraDescuento.ajuste(new BigDecimal("450.00"), MONTO, new BigDecimal("50"))).isEqualByComparingTo("50.00");
		assertThatThrownBy(() -> CalculadoraDescuento.ajuste(new BigDecimal("450.00"), MONTO, new BigDecimal("450.10")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("mayor que la cuota");
		assertThatThrownBy(() -> CalculadoraDescuento.ajuste(new BigDecimal("450.00"), MONTO, new BigDecimal("50.05")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("múltiplo de S/ 0.10");
		assertThatThrownBy(() -> CalculadoraDescuento.ajuste(new BigDecimal("450.00"), PORCENTAJE, new BigDecimal("100.01")))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> CalculadoraDescuento.ajuste(new BigDecimal("450.00"), PORCENTAJE, BigDecimal.ZERO))
				.isInstanceOf(ReglaNegocioException.class);
		// Contra el saldo (lo que falta pagar): una cuota con S/ 400 pagados no admite S/ 100 de descuento.
		Cuota parcial = CuotasDePrueba.pensionSetiembre("450.00");
		parcial.reflejarPagos(new BigDecimal("400.00"));
		assertThatThrownBy(() -> parcial.reflejarDescuentos(new BigDecimal("100.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("supera");
	}

	/**
	 * Hallazgo 10 de QA: un descuento de 99.99 % deja un saldo que se puede cobrar exacto en efectivo (múltiplo de
	 * S/ 0.10, redondeado a favor del apoderado). En una cuota de S/ 450.00 el 0.01 % es S/ 0.045: el saldo queda en
	 * S/ 0.00 (la cuota no se cobra, como una beca completa); en una de S/ 4,500.00, en S/ 0.40.
	 */
	@Test
	void descuentoDe99_99PorCientoDejaSaldoCobrableEnEfectivo() {
		BigDecimal ajuste = CalculadoraDescuento.ajuste(new BigDecimal("450.00"), PORCENTAJE, new BigDecimal("99.99"));
		assertThat(new BigDecimal("450.00").subtract(ajuste)).isEqualByComparingTo("0.00");
		BigDecimal grande = CalculadoraDescuento.ajuste(new BigDecimal("4500.00"), PORCENTAJE, new BigDecimal("99.99"));
		BigDecimal saldo = new BigDecimal("4500.00").subtract(grande);
		assertThat(saldo).isEqualByComparingTo("0.40");
		assertThat(Dinero.enDecimos(saldo)).isTrue();
	}
}
