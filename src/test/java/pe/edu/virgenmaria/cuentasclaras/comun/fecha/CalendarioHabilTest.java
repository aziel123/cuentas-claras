package pe.edu.virgenmaria.cuentasclaras.comun.fecha;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 5, tanda 3 (hallazgo 6 y G20): el día hábil salta los 16 feriados nacionales (en el código) y los días no
 * laborables del colegio (de la base, por colegio y con caché).
 */
class CalendarioHabilTest {

	private static LocalDate d(int anio, int mes, int dia) {
		return LocalDate.of(anio, mes, dia);
	}

	@Test
	void feriadosNacionales2027() {
		assertThat(FeriadosNacionales.de(2027)).hasSize(16).containsExactly(d(2027, 1, 1), d(2027, 3, 25),
				d(2027, 3, 26), d(2027, 5, 1), d(2027, 6, 7), d(2027, 6, 29), d(2027, 7, 23), d(2027, 7, 28),
				d(2027, 7, 29), d(2027, 8, 6), d(2027, 8, 30), d(2027, 10, 8), d(2027, 11, 1), d(2027, 12, 8),
				d(2027, 12, 9), d(2027, 12, 25));
		assertThat(FeriadosNacionales.conNombres(2027)).containsEntry(d(2027, 8, 30), "Santa Rosa de Lima");
	}

	@Test
	void semanaSanta2027() {
		assertThat(FeriadosNacionales.pascua(2027)).isEqualTo(d(2027, 3, 28));
		assertThat(FeriadosNacionales.pascua(2026)).isEqualTo(d(2026, 4, 5));
		assertThat(FeriadosNacionales.conNombres(2027)).containsEntry(d(2027, 3, 25), "Jueves Santo")
				.containsEntry(d(2027, 3, 26), "Viernes Santo");
		assertThat(FeriadosNacionales.es(d(2026, 4, 2))).isTrue();
		assertThat(FeriadosNacionales.es(d(2026, 4, 3))).isTrue();
		// El miércoles 24 de marzo de 2027, el hábil siguiente es el lunes 29.
		assertThat(DiasHabiles.NACIONALES.siguienteDiaHabil(d(2027, 3, 24))).isEqualTo(d(2027, 3, 29));
	}

	@Test
	void elOchoDeDiciembreNoCuentaComoHabil() {
		// «Terminado cuando»: el 08/12 no cuenta en la alerta de depósito. Lunes 7/12/2026 → el siguiente es el jueves 10.
		assertThat(DiasHabiles.NACIONALES.esHabil(d(2026, 12, 8))).isFalse();
		assertThat(DiasHabiles.NACIONALES.siguienteDiaHabil(d(2026, 12, 7))).isEqualTo(d(2026, 12, 10));
		assertThat(DiasHabiles.NACIONALES.habilesEntre(d(2026, 12, 7), d(2026, 12, 10))).isEqualTo(1);
		// Sin feriados (lo de antes) el martes 8 contaba.
		assertThat(DiasHabiles.LUNES_A_VIERNES.siguienteDiaHabil(d(2026, 12, 7))).isEqualTo(d(2026, 12, 8));
	}

	@Test
	void feriadoExtraDelColegio() {
		FeriadoRepository repositorio = mock(FeriadoRepository.class);
		when(repositorio.findByVigenteTrue()).thenReturn(List.of(Feriado.nuevo(d(2026, 10, 9), "Aniversario del colegio")));
		CalendarioHabil calendario = new CalendarioHabil(repositorio);

		ContextoColegio.en(1L, () -> {
			assertThat(calendario.esHabil(d(2026, 10, 9))).isFalse();
			// Jueves 8 es Angamos y viernes 9 el día del colegio: del miércoles 7 se pasa al lunes 12.
			assertThat(calendario.siguienteDiaHabil(d(2026, 10, 7))).isEqualTo(d(2026, 10, 12));
			assertThat(calendario.delColegio()).containsExactly(d(2026, 10, 9));
		});
		// Otro colegio, o ninguno, no ve ese día (los nacionales sí).
		when(repositorio.findByVigenteTrue()).thenReturn(List.of());
		ContextoColegio.en(2L, () -> assertThat(calendario.esHabil(d(2026, 10, 9))).isTrue());
		assertThat(calendario.esHabil(d(2026, 10, 9))).isTrue();
		assertThat(calendario.esHabil(d(2026, 10, 8))).isFalse();
		// Con caché: el colegio 1 no vuelve a leer la base hasta que se invalida.
		ContextoColegio.en(1L, () -> assertThat(calendario.esHabil(d(2026, 10, 9))).isFalse());
		verify(repositorio, times(2)).findByVigenteTrue();
		calendario.invalidar(1L);
		ContextoColegio.en(1L, () -> assertThat(calendario.esHabil(d(2026, 10, 9))).isTrue());
	}

	@Test
	void siguienteHabilSaltaElFeriado() {
		// Fiestas Patrias 2027: miércoles 28 y jueves 29 de julio. Del martes 27 se pasa al viernes 30.
		assertThat(DiasHabiles.NACIONALES.siguienteDiaHabil(d(2027, 7, 27))).isEqualTo(d(2027, 7, 30));
		assertThat(DiasHabiles.NACIONALES.anteriorDiaHabil(d(2027, 7, 30))).isEqualTo(d(2027, 7, 27));
		assertThat(DiasHabiles.NACIONALES.sumarHabiles(d(2027, 7, 27), 2)).isEqualTo(d(2027, 8, 2));
		// Mensajes: lunes a sábado, sin feriados. El domingo 27/12/2026 se adelanta al sábado 26.
		assertThat(DiasHabiles.NACIONALES.admiteMensajes(d(2026, 12, 26))).isTrue();
		assertThat(DiasHabiles.NACIONALES.diaDeMensajesEnOAntes(d(2026, 12, 27))).isEqualTo(d(2026, 12, 26));
		// Navidad (viernes) y el domingo: el 25 se adelanta al jueves 24.
		assertThat(DiasHabiles.NACIONALES.diaDeMensajesEnOAntes(d(2026, 12, 25))).isEqualTo(d(2026, 12, 24));
	}
}
