package pe.edu.virgenmaria.cuentasclaras.caja.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.CuotaPorPagar;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.Imputacion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Imputación: se paga primero lo que vence antes; solo la última cuota puede quedar parcial (diseño, sección 10.2). */
class ImputacionPagoTest {

	private static final CuotaPorPagar SETIEMBRE = cuota(30L, 2026, 9, "450.00");

	private static final CuotaPorPagar OCTUBRE = cuota(20L, 2026, 10, "450.00");

	private static final CuotaPorPagar NOVIEMBRE = cuota(10L, 2026, 11, "450.00");

	@Test
	void pagaPrimeroLaCuotaMasAntigua() {
		// Llegan desordenadas (por id): se imputan por vencimiento.
		List<Imputacion> imputaciones = ImputacionPago.imputar(new BigDecimal("450.00"), List.of(NOVIEMBRE, SETIEMBRE, OCTUBRE));

		assertThat(imputaciones).containsExactly(new Imputacion(30L, new BigDecimal("450.00")));
	}

	@Test
	void soloLaUltimaQuedaParcial() {
		List<Imputacion> imputaciones = ImputacionPago.imputar(new BigDecimal("1000"), List.of(OCTUBRE, NOVIEMBRE, SETIEMBRE));

		assertThat(imputaciones).extracting(Imputacion::cuotaId).containsExactly(30L, 20L, 10L);
		assertThat(imputaciones).extracting(Imputacion::monto).usingElementComparator(BigDecimal::compareTo)
				.containsExactly(new BigDecimal("450.00"), new BigDecimal("450.00"), new BigDecimal("100.00"));
		assertThat(imputaciones.get(2).monto().scale()).isEqualTo(2);
	}

	@Test
	void montoMayorQueLasCuotasEsRechazado() {
		assertThatThrownBy(() -> ImputacionPago.imputar(new BigDecimal("900.01"), List.of(SETIEMBRE, OCTUBRE)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("supera lo que se debe");
		assertThatThrownBy(() -> ImputacionPago.imputar(BigDecimal.ZERO, List.of(SETIEMBRE)))
				.isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> ImputacionPago.imputar(new BigDecimal("1.001"), List.of(SETIEMBRE)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("2 decimales");
	}

	@Test
	void empateDeVencimientoOrdenaPorId() {
		CuotaPorPagar mateo = cuota(8L, 2026, 12, "450.00");
		CuotaPorPagar valeria = cuota(5L, 2026, 12, "380.00");

		List<Imputacion> imputaciones = ImputacionPago.imputar(new BigDecimal("500.00"), List.of(mateo, valeria));

		assertThat(imputaciones).extracting(Imputacion::cuotaId).containsExactly(5L, 8L);
		assertThat(imputaciones.get(1).monto()).isEqualByComparingTo("120.00");
	}

	private static CuotaPorPagar cuota(Long id, int anio, int mes, String saldo) {
		return new CuotaPorPagar(id, LocalDate.of(anio, mes, 1).withDayOfMonth(LocalDate.of(anio, mes, 1).lengthOfMonth()),
				new BigDecimal(saldo));
	}
}
