package pe.edu.virgenmaria.cuentasclaras.cobranza.model;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VENCIDA se calcula con la fecha de Lima (UTC-5): la cuota que vence el 31/03/2027 está al día hasta las 23:59:59 de
 * Lima (04:59:59 UTC del 01/04) y vencida desde las 00:00 de Lima.
 */
class EstadoCuotaTest {

	@Test
	void queVenceHoyNoEstaVencidaALas2359DeLima() {
		Cuota marzo = pensionDeMarzo();
		RelojAjustable reloj = new RelojAjustable(Instant.parse("2027-04-01T04:59:59Z"), ConfiguracionTiempo.ZONA_LIMA);

		assertThat(LocalDate.now(reloj)).isEqualTo(LocalDate.of(2027, 3, 31));
		assertThat(marzo.estadoAl(LocalDate.now(reloj))).isEqualTo(EstadoVisibleCuota.PENDIENTE);
	}

	@Test
	void alDiaSiguienteALas0000DeLimaEstaVencida() {
		Cuota marzo = pensionDeMarzo();
		RelojAjustable reloj = new RelojAjustable(Instant.parse("2027-04-01T05:00:00Z"), ConfiguracionTiempo.ZONA_LIMA);

		assertThat(marzo.estadoAl(LocalDate.now(reloj))).isEqualTo(EstadoVisibleCuota.VENCIDA);
		assertThat(marzo.vencidaAl(LocalDate.now(reloj))).isTrue();
		// El estado guardado no cambia: VENCIDA nunca se guarda.
		assertThat(marzo.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
	}

	@Test
	void pagadaNuncaSeMuestraVencida() throws Exception {
		Cuota marzo = pensionDeMarzo();
		forzar(marzo, "estado", EstadoCuota.PAGADA);
		forzar(marzo, "montoPagado", marzo.getMonto());

		assertThat(marzo.estadoAl(LocalDate.of(2030, 1, 1))).isEqualTo(EstadoVisibleCuota.PAGADA);
		assertThat(marzo.saldo()).isEqualByComparingTo("0.00");
	}

	@Test
	void parcialVencidaSeMuestraVencidaYSuSaldoEsLoQueFalta() throws Exception {
		Cuota marzo = pensionDeMarzo();
		forzar(marzo, "estado", EstadoCuota.PARCIAL);
		forzar(marzo, "montoPagado", new BigDecimal("100.00"));

		assertThat(marzo.estadoAl(LocalDate.of(2027, 3, 31))).isEqualTo(EstadoVisibleCuota.PARCIAL);
		assertThat(marzo.estadoAl(LocalDate.of(2027, 4, 1))).isEqualTo(EstadoVisibleCuota.VENCIDA);
		assertThat(marzo.saldo()).isEqualByComparingTo("350.00");
	}

	@Test
	void anuladaNoSumaAlSaldo() {
		Cuota marzo = pensionDeMarzo();
		marzo.anular("La familia se retiró antes de marzo", "administracion", "director", LocalDateTime.of(2027, 3, 2, 9, 0));

		assertThat(marzo.estadoAl(LocalDate.of(2027, 6, 1))).isEqualTo(EstadoVisibleCuota.ANULADA);
		assertThat(marzo.saldo()).isEqualByComparingTo("0.00");
		assertThat(marzo.getMonto()).isEqualByComparingTo("450.00");
	}

	private static Cuota pensionDeMarzo() {
		PlanPension plan = CuotasDePrueba.planAprobado("450.00", "0");
		CuotaPlanificada marzo = CalculadoraCronograma.calcular(plan, 1L, LocalDate.of(2027, 3, 1)).get(0);
		assertThat(marzo.vencimiento()).isEqualTo(LocalDate.of(2027, 3, 31));
		return Cuota.generada(CuotasDePrueba.matricula(LocalDate.of(2027, 3, 1)), plan, marzo);
	}

	/** Solo para simular pagos (llegan en el sprint 3): la entidad no tiene setters. */
	private static void forzar(Cuota cuota, String campo, Object valor) throws Exception {
		Field f = Cuota.class.getDeclaredField(campo);
		f.setAccessible(true);
		f.set(cuota, valor);
	}
}
