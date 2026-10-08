package pe.edu.virgenmaria.cuentasclaras.recaudacion.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.caja.model.ImputacionPago.Imputacion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.model.MotivoExcepcion;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ReglasRecaudacion.Deuda;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.ReglasRecaudacion.Resolucion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 4: bordes de la imputación de la recaudación bancaria (sección 10.2): céntimos, cuotas con descuento de
 * hermanos y beca del 100 %, empates de vencimiento, orden de las excepciones y parciales desactivados. Puro.
 */
class ReglasRecaudacionBordesTest {

	private static final Long MATEO = 1L;

	private static final LocalDate SETIEMBRE_VENCE = LocalDate.of(2026, 9, 30);

	private static final LocalDate OCTUBRE_VENCE = LocalDate.of(2026, 10, 31);

	private static Deuda deuda(long id, LocalDate vence, String saldo) {
		return new Deuda(id, MATEO, vence, new BigDecimal(saldo), true, "Cuota " + id);
	}

	private static Resolucion resolver(String monto, Deuda referida, List<Deuda> deudas) {
		return ReglasRecaudacion.resolver(new BigDecimal(monto), "PEN", false, MATEO, referida, deudas, true);
	}

	private static Resolucion sinParciales(String monto, Deuda referida, List<Deuda> deudas) {
		return ReglasRecaudacion.resolver(new BigDecimal(monto), "PEN", false, MATEO, referida, deudas, false);
	}

	@Test
	void debeImputarUnCentimoALaSegundaCuotaSinRedondear() {
		Resolucion r = resolver("450.01", null, List.of(deuda(10, SETIEMBRE_VENCE, "450.00"),
				deuda(11, OCTUBRE_VENCE, "450.00")));

		assertThat(r.aplicable()).isTrue();
		assertThat(r.imputaciones()).hasSize(2);
		assertThat(r.imputaciones().get(0).monto()).isEqualByComparingTo("450.00");
		assertThat(r.imputaciones().get(1).monto()).isEqualByComparingTo("0.01");
		assertThat(r.aCuenta()).isTrue();
	}

	@Test
	void debeCubrirExactamenteDosCuotasConDescuentoDeHermanosSinQuedarACuenta() {
		// Setiembre con 10 % de hermanos (405.00) y octubre completa (450.00).
		Resolucion r = resolver("855.00", null, List.of(deuda(11, OCTUBRE_VENCE, "450.00"),
				deuda(10, SETIEMBRE_VENCE, "405.00")));

		assertThat(r.aplicable()).isTrue();
		assertThat(r.imputaciones()).extracting(Imputacion::cuotaId).containsExactly(10L, 11L);
		assertThat(r.imputaciones().get(0).monto()).isEqualByComparingTo("405.00");
		assertThat(r.imputaciones().get(1).monto()).isEqualByComparingTo("450.00");
		assertThat(r.aCuenta()).isFalse();
	}

	@Test
	void debeMarcarExcesoPorUnCentimoSobreLaDeudaConDescuento() {
		assertThat(resolver("855.01", null, List.of(deuda(10, SETIEMBRE_VENCE, "405.00"),
				deuda(11, OCTUBRE_VENCE, "450.00"))).motivo()).isEqualTo(MotivoExcepcion.EXCESO);
	}

	@Test
	void debeIgnorarLaCuotaExoneradaPorBecaDelCienPorCiento() {
		Deuda exonerada = new Deuda(10L, MATEO, SETIEMBRE_VENCE, new BigDecimal("0.00"), false, "Setiembre (beca)");

		Resolucion r = resolver("450.00", null, List.of(exonerada, deuda(11, OCTUBRE_VENCE, "450.00")));

		assertThat(r.imputaciones()).containsExactly(new Imputacion(11L, new BigDecimal("450.00")));
	}

	@Test
	void debeTratarComoSinDeudaAlAlumnoConTodasSusCuotasExoneradas() {
		Deuda exonerada = new Deuda(10L, MATEO, SETIEMBRE_VENCE, new BigDecimal("0.00"), false, "Setiembre (beca)");

		assertThat(resolver("450.00", null, List.of(exonerada)).motivo()).isEqualTo(MotivoExcepcion.ALUMNO_SIN_DEUDA);
	}

	@Test
	void debeIgnorarUnaCuotaCobrableConSaldoCero() {
		Deuda sinSaldo = new Deuda(10L, MATEO, SETIEMBRE_VENCE, new BigDecimal("0.00"), true, "Setiembre");

		assertThat(resolver("100.00", null, List.of(sinSaldo)).motivo()).isEqualTo(MotivoExcepcion.ALUMNO_SIN_DEUDA);
	}

	@Test
	void debeIgnorarUnaCuotaConAnulacionPendienteAunqueTengaSaldo() {
		Deuda enAnulacion = new Deuda(10L, MATEO, SETIEMBRE_VENCE, new BigDecimal("450.00"), false, "Setiembre");

		Resolucion r = resolver("450.00", null, List.of(enAnulacion, deuda(11, OCTUBRE_VENCE, "450.00")));

		assertThat(r.imputaciones()).containsExactly(new Imputacion(11L, new BigDecimal("450.00")));
	}

	@Test
	void debeImputarPrimeroLaCuotaDeMenorIdCuandoVencenElMismoDia() {
		Resolucion r = resolver("450.00", null, List.of(deuda(21, OCTUBRE_VENCE, "450.00"),
				deuda(20, OCTUBRE_VENCE, "350.00")));

		assertThat(r.imputaciones()).extracting(Imputacion::cuotaId).containsExactly(20L, 21L);
		assertThat(r.imputaciones().get(1).monto()).isEqualByComparingTo("100.00");
	}

	@Test
	void debeImputarALaCuotaMasAntiguaAunqueVengaAlFinalDeLaLista() {
		Resolucion r = resolver("200.00", null, List.of(deuda(30, OCTUBRE_VENCE, "450.00"),
				deuda(31, SETIEMBRE_VENCE, "450.00")));

		assertThat(r.imputaciones()).containsExactly(new Imputacion(31L, new BigDecimal("200.00")));
	}

	@Test
	void debeAceptarElPagoExactoDeLaDeudaTotalSinExceso() {
		Resolucion r = sinParciales("855.00", null, List.of(deuda(10, SETIEMBRE_VENCE, "405.00"),
				deuda(11, OCTUBRE_VENCE, "450.00")));

		assertThat(r.aplicable()).isTrue();
		assertThat(r.aCuenta()).isFalse();
	}

	@Test
	void debeRechazarUnParcialConReferenciaSiLosParcialesEstanDesactivados() {
		assertThat(sinParciales("449.99", deuda(11, OCTUBRE_VENCE, "450.00"), List.of()).motivo())
				.isEqualTo(MotivoExcepcion.PAGO_PARCIAL);
	}

	@Test
	void debeAceptarConReferenciaElPagoExactoConParcialesDesactivados() {
		Resolucion r = sinParciales("450.00", deuda(11, OCTUBRE_VENCE, "450.00"), List.of());

		assertThat(r.aplicable()).isTrue();
		assertThat(r.aCuenta()).isFalse();
	}

	@Test
	void debeRechazarUnaCuotaReferidaConSaldoPeroNoCobrable() {
		Deuda referida = new Deuda(11L, MATEO, OCTUBRE_VENCE, new BigDecimal("450.00"), false, "Octubre");

		assertThat(resolver("450.00", referida, List.of()).motivo()).isEqualTo(MotivoExcepcion.CUOTA_NO_COBRABLE);
	}

	@Test
	void debeIgnorarLasDemasCuotasCuandoHayReferencia() {
		// Con referencia el dinero va a ESA cuota aunque haya una más antigua sin pagar.
		Resolucion r = resolver("450.00", deuda(11, OCTUBRE_VENCE, "450.00"), List.of(deuda(10, SETIEMBRE_VENCE,
				"450.00")));

		assertThat(r.imputaciones()).containsExactly(new Imputacion(11L, new BigDecimal("450.00")));
	}

	@Test
	void debeEvaluarLaMonedaAntesQueLaOperacionDuplicada() {
		assertThat(ReglasRecaudacion.resolver(new BigDecimal("450.00"), "USD", true, null, null, List.of(), true).motivo())
				.isEqualTo(MotivoExcepcion.MONEDA);
	}

	@Test
	void debeEvaluarLaOperacionDuplicadaAntesQueElCodigoInvalido() {
		assertThat(ReglasRecaudacion.resolver(new BigDecimal("450.00"), "PEN", true, null, null, List.of(), true).motivo())
				.isEqualTo(MotivoExcepcion.OPERACION_DUPLICADA);
	}

	@Test
	void debeRechazarMontosConMasDeDosDecimalesSinRedondear() {
		assertThatThrownBy(() -> resolver("450.005", null, List.of(deuda(10, SETIEMBRE_VENCE, "450.00"))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("2 decimales");
	}

	@Test
	void debeMarcarACuentaElPagoQueDejaUnCentimoPendiente() {
		Resolucion r = resolver("899.99", null, List.of(deuda(10, SETIEMBRE_VENCE, "450.00"),
				deuda(11, OCTUBRE_VENCE, "450.00")));

		assertThat(r.aCuenta()).isTrue();
		assertThat(r.imputaciones().get(1).monto()).isEqualByComparingTo("449.99");
	}
}
