package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.EstadoMatricula;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.Tramos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.AjusteCuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Reglas de agregación de {@link CifrasCobranza} sin base de datos (los JPQL se prueban en PanelPromotoriaTest). */
class CifrasCobranzaTest {

	private static final LocalDate AL = LocalDate.of(2027, 6, 30);

	private final CuotaRepository cuotas = mock(CuotaRepository.class);

	private final CifrasCobranza cifras = new CifrasCobranza(cuotas, mock(AjusteCuotaRepository.class),
			mock(AnioEscolarRepository.class));

	private static Object[] vencido(long familia, long alumno, long anio, LocalDate masAntigua, String saldo) {
		return new Object[] { familia, "Familia " + familia, alumno, anio, masAntigua, new BigDecimal(saldo), 1L };
	}

	/** Hallazgo 8: una cuota de un alumno sin matrícula en el año (saldo inicial) va a «Sin matrícula en ese año». */
	@Test
	void cuotaSinMatriculaEnElAnioVaASuPropiaFila() {
		when(cuotas.gradosDelAnio(1L)).thenReturn(List.<Object[]>of(new Object[] { 10L, Grado.PRIMARIA_6,
				EstadoMatricula.ACTIVA }, new Object[] { 11L, Grado.PRIMARIA_6, EstadoMatricula.RETIRADA }));
		when(cuotas.vencidasPorAlumno(AL)).thenReturn(List.of(vencido(1, 10, 1, AL.minusDays(10), "450.00"),
				vencido(2, 20, 1, AL.minusDays(95), "120.50"), vencido(3, 30, 2, AL.minusDays(40), "999.00")));

		List<MorosidadGrado> filas = cifras.morosidadPorGrado(1L, AL);

		assertThat(filas).hasSize(2);
		assertThat(filas.get(0).grado()).isEqualTo(Grado.PRIMARIA_6);
		assertThat(filas.get(0).matriculados()).as("solo matrículas activas").isEqualTo(1);
		assertThat(filas.get(0).monto()).isEqualByComparingTo("450.00");
		assertThat(filas.get(1).grado()).isNull();
		assertThat(filas.get(1).etiqueta()).isEqualTo("Sin matrícula en ese año");
		assertThat(filas.get(1).monto()).as("la cuota de otro año no entra").isEqualByComparingTo("120.50");
		assertThat(filas.get(1).tramos()).isEqualTo(new Tramos(0, 0, 0, 1));
	}

	@Test
	void tramosEnSusBordes() {
		Tramos t = Tramos.NINGUNO.sumar(1).sumar(30).sumar(31).sumar(60).sumar(61).sumar(90).sumar(91);
		assertThat(t).isEqualTo(new Tramos(2, 2, 2, 1));
		assertThat(t.total()).isEqualTo(7);
	}

	/** Una familia con dos hijos morosos cuenta una vez, en el tramo de su cuota más antigua. */
	@Test
	void unaFamiliaCuentaUnaVezPorSuCuotaMasAntigua() {
		when(cuotas.vencidasPorAlumno(AL)).thenReturn(List.of(vencido(1, 10, 1, AL.minusDays(5), "100.00"),
				vencido(1, 11, 1, AL.minusDays(70), "200.00")));
		var deuda = cifras.deudaVencida(AL);
		assertThat(deuda.familias()).isEqualTo(1);
		assertThat(deuda.monto()).isEqualByComparingTo("300.00").hasScaleOf(2);
		assertThat(deuda.tramos()).isEqualTo(new Tramos(0, 0, 1, 0));
		assertThat(cifras.familiasMorosas(AL)).singleElement().satisfies(f -> {
			assertThat(f.alumnos()).isEqualTo(2);
			assertThat(f.dias()).isEqualTo(70);
		});
	}
}
