package pe.edu.virgenmaria.cuentasclaras.comun.fecha;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarioTest {

	@Test
	void nacidoEl29DeFebreroDe2020Tiene3AniosAl31DeMarzoDe2023() {
		assertThat(Calendario.edadAl31DeMarzo(LocalDate.of(2020, 2, 29), 2023)).isEqualTo(3);
	}

	@Test
	void nacidoEl1DeAbrilNoCumpleAntesDel31DeMarzo() {
		assertThat(Calendario.edadAl31DeMarzo(LocalDate.of(2020, 4, 1), 2026)).isEqualTo(5);
		assertThat(Calendario.edadAl31DeMarzo(LocalDate.of(2020, 3, 31), 2026)).isEqualTo(6);
	}

	@Test
	void nombreDelMesEsSetiembreSinImportarElLocaleDeLaJvm() {
		Locale anterior = Locale.getDefault();
		try {
			for (Locale jvm : new Locale[] { Locale.US, Locale.forLanguageTag("es-ES"), Locale.GERMANY }) {
				Locale.setDefault(jvm);
				assertThat(Calendario.nombreMes(9)).isEqualTo("setiembre");
			}
			assertThat(Calendario.nombreMes(3)).isEqualTo("marzo");
		}
		finally {
			Locale.setDefault(anterior);
		}
	}

	@Test
	void fechaConAnioDeDosDigitosEsRechazada() {
		assertThat(Calendario.parsearFecha("5/3/2015")).isEqualTo(LocalDate.of(2015, 3, 5));
		assertThat(Calendario.parsearFecha("05/03/2015")).isEqualTo(LocalDate.of(2015, 3, 5));
		assertThatThrownBy(() -> Calendario.parsearFecha("05/03/15"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("dd/mm/aaaa");
	}

	@Test
	void fecha29DeFebreroDe2015NoExiste() {
		assertThatThrownBy(() -> Calendario.parsearFecha("29/02/2015")).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> Calendario.parsearFecha("31/04/2016")).isInstanceOf(ReglaNegocioException.class);
		assertThat(Calendario.parsearFecha("29/02/2016")).isEqualTo(LocalDate.of(2016, 2, 29));
	}

	@Test
	void vencimientosPorDefectoSonElUltimoDiaDeCadaMes() {
		assertThat(Calendario.vencimientosPorDefecto(2027, 3, 10)).hasSize(10)
				.startsWith(LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30))
				.endsWith(LocalDate.of(2027, 12, 31));
		assertThat(Calendario.ultimoDiaDelMes(2028, 2)).isEqualTo(LocalDate.of(2028, 2, 29));
	}
}
