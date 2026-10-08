package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.Imputacion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.MotivoExcepcion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ReglasRecaudacion.Deuda;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ReglasRecaudacion.Resolucion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A qué cuotas va un pago hecho en el banco (sección 10.2 del diseño): la misma imputación de caja. Puro. */
class ReglasRecaudacionTest {

	private static final Long MATEO = 1L;

	private static final Deuda SETIEMBRE = new Deuda(10L, MATEO, LocalDate.of(2026, 9, 30), new BigDecimal("450.00"), true,
			"Pensión setiembre");

	private static final Deuda OCTUBRE = new Deuda(11L, MATEO, LocalDate.of(2026, 10, 31), new BigDecimal("450.00"), true,
			"Pensión octubre");

	private static final Deuda PAGADA = new Deuda(9L, MATEO, LocalDate.of(2026, 8, 31), new BigDecimal("0.00"), false,
			"Pensión agosto");

	private static Resolucion resolver(String monto, Deuda referida, List<Deuda> deudas) {
		return ReglasRecaudacion.resolver(new BigDecimal(monto), "PEN", false, MATEO, referida, deudas, true);
	}

	@Test
	void sinReferenciaImputaDeLaMasAntigua() {
		Resolucion completas = resolver("900.00", null, List.of(OCTUBRE, PAGADA, SETIEMBRE));
		assertThat(completas.aplicable()).isTrue();
		assertThat(completas.imputaciones()).extracting(Imputacion::cuotaId).containsExactly(10L, 11L);
		assertThat(completas.aCuenta()).isFalse();

		Resolucion parcial = resolver("500.00", null, List.of(OCTUBRE, SETIEMBRE));
		assertThat(parcial.imputaciones()).containsExactly(new Imputacion(10L, new BigDecimal("450.00")),
				new Imputacion(11L, new BigDecimal("50.00")));
		assertThat(parcial.aCuenta()).isTrue();
	}

	@Test
	void conReferenciaVaALaCuotaExacta() {
		Resolucion exacta = resolver("450.00", OCTUBRE, List.of());
		assertThat(exacta.imputaciones()).containsExactly(new Imputacion(11L, new BigDecimal("450.00")));
		assertThat(exacta.aCuenta()).isFalse();
		assertThat(resolver("200.00", OCTUBRE, List.of()).aCuenta()).isTrue();
		assertThat(resolver("450.00", PAGADA, List.of()).motivo()).isEqualTo(MotivoExcepcion.CUOTA_NO_COBRABLE);
		Deuda deOtroAlumno = new Deuda(20L, 2L, LocalDate.of(2026, 10, 31), new BigDecimal("450.00"), true, "Pensión");
		assertThat(resolver("450.00", deOtroAlumno, List.of()).motivo()).isEqualTo(MotivoExcepcion.CUOTA_NO_COBRABLE);
	}

	@Test
	void excesoSinDeudaYParcialesDesactivados() {
		assertThat(resolver("900.01", null, List.of(SETIEMBRE, OCTUBRE)).motivo()).isEqualTo(MotivoExcepcion.EXCESO);
		assertThat(resolver("450.01", OCTUBRE, List.of()).motivo()).isEqualTo(MotivoExcepcion.EXCESO);
		assertThat(resolver("450.00", null, List.of(PAGADA)).motivo()).isEqualTo(MotivoExcepcion.ALUMNO_SIN_DEUDA);
		assertThat(ReglasRecaudacion.resolver(new BigDecimal("100.00"), "PEN", false, MATEO, null, List.of(SETIEMBRE),
				false).motivo()).isEqualTo(MotivoExcepcion.PAGO_PARCIAL);
	}

	@Test
	void usdOperacionUsadaYCodigoSinAlumnoSonExcepcion() {
		assertThat(ReglasRecaudacion.resolver(new BigDecimal("450.00"), "USD", false, MATEO, null, List.of(SETIEMBRE),
				true).motivo()).isEqualTo(MotivoExcepcion.MONEDA);
		assertThat(ReglasRecaudacion.resolver(new BigDecimal("450.00"), "PEN", true, MATEO, null, List.of(SETIEMBRE),
				true).motivo()).isEqualTo(MotivoExcepcion.OPERACION_DUPLICADA);
		assertThat(ReglasRecaudacion.resolver(new BigDecimal("450.00"), "PEN", false, null, null, List.of(), true)
				.motivo()).isEqualTo(MotivoExcepcion.CODIGO_INVALIDO);
	}
}
