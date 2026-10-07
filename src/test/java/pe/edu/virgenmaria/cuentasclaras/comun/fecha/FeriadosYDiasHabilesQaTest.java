package pe.edu.virgenmaria.cuentasclaras.comun.fecha;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA sprint 5 (tanda 3): feriados nacionales fijos en el código y cálculo de días hábiles y de días de mensajes.
 * Dado un año cualquiera, cuando se piden sus feriados, entonces son los 16 de ley y la Semana Santa sale de la Pascua.
 */
class FeriadosYDiasHabilesQaTest {

	private static final DiasHabiles NACIONALES = DiasHabiles.NACIONALES;

	@ParameterizedTest
	@CsvSource({ "2024,2024-03-31", "2025,2025-04-20", "2026,2026-04-05", "2027,2027-03-28", "2028,2028-04-16",
			"2029,2029-04-01", "2030,2030-04-21", "2035,2035-03-25", "2032,2032-03-28" })
	void debeCalcularElDomingoDePascuaGregoriano(int anio, LocalDate pascua) {
		assertThat(FeriadosNacionales.pascua(anio)).isEqualTo(pascua);
	}

	@ParameterizedTest
	@ValueSource(ints = { 2026, 2027, 2028, 2029, 2030, 2031, 2032, 2033, 2034, 2035 })
	void debeHaberSiempreDieciseisFeriadosDistintosDeEseAnio(int anio) {
		assertThat(FeriadosNacionales.de(anio)).hasSize(16).allSatisfy(f -> assertThat(f.getYear()).isEqualTo(anio));
	}

	@Test
	void laMatriculaQueVenceElDomingo28DeFebreroDe2027TieneComoHabilSiguienteElLunes() {
		LocalDate vence = LocalDate.of(2027, 2, 28);
		assertThat(NACIONALES.esHabil(vence)).isFalse();
		assertThat(NACIONALES.siguienteDiaHabil(vence)).isEqualTo(LocalDate.of(2027, 3, 1));
	}

	@Test
	void debeContarLosHabilesSaltandoFiestasPatrias2027() {
		// Martes 27 de julio → lunes 2 de agosto de 2027: el 28 y el 29 son feriados; quedan el viernes 30 y el lunes 2.
		assertThat(NACIONALES.habilesEntre(LocalDate.of(2027, 7, 27), LocalDate.of(2027, 8, 2))).isEqualTo(2);
		// El orden de las fechas no importa.
		assertThat(NACIONALES.habilesEntre(LocalDate.of(2027, 8, 2), LocalDate.of(2027, 7, 27))).isEqualTo(2);
	}

	@Test
	void debeRetrocederUnHabilSobreLaSemanaSanta2027() {
		assertThat(NACIONALES.sumarHabiles(LocalDate.of(2027, 3, 29), -1)).isEqualTo(LocalDate.of(2027, 3, 24));
		assertThat(NACIONALES.sumarHabiles(LocalDate.of(2027, 3, 24), 1)).isEqualTo(LocalDate.of(2027, 3, 29));
		assertThat(NACIONALES.sumarHabiles(LocalDate.of(2027, 3, 24), 0)).isEqualTo(LocalDate.of(2027, 3, 24));
	}

	@Test
	void elSabadoAdmiteMensajesPeroNoSiEsFeriado() {
		assertThat(NACIONALES.admiteMensajes(LocalDate.of(2027, 3, 27))).isTrue(); // sábado común
		assertThat(NACIONALES.admiteMensajes(LocalDate.of(2027, 5, 1))).isFalse(); // sábado 1 de mayo
		assertThat(NACIONALES.admiteMensajes(LocalDate.of(2027, 5, 2))).isFalse(); // domingo
	}

	@Test
	void unRecordatorioQueCaeEnDomingoTrasUnSabadoFeriadoSaleElViernes() {
		assertThat(NACIONALES.diaDeMensajesEnOAntes(LocalDate.of(2027, 5, 2))).isEqualTo(LocalDate.of(2027, 4, 30));
		assertThat(NACIONALES.diaDeMensajesEnODespues(LocalDate.of(2027, 5, 1))).isEqualTo(LocalDate.of(2027, 5, 3));
	}

	@Test
	void sinFeriadosSoloSeDescartanLosFinesDeSemana() {
		assertThat(DiasHabiles.LUNES_A_VIERNES.esHabil(LocalDate.of(2026, 12, 8))).isTrue();
		assertThat(NACIONALES.esHabil(LocalDate.of(2026, 12, 8))).isFalse();
	}
}
