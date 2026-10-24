package pe.edu.virgenmaria.cuentasclaras.academico.curriculo;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.model.TipoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA del sprint 10 (S1, decisión 171): además del bimestre por defecto, el colegio puede evaluar por trimestres o
 * semestres. El año tiene exactamente sus 3 o 2 periodos, con su nombre, y el siguiente se rechaza; el tipo queda fijo
 * desde que abre el primero.
 */
class QaSprint10PeriodosTest extends PruebaCurriculo {

	@Test
	void debeProgramarTresTrimestresConSuNombreYRechazarElCuarto() {
		como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.TRIMESTRE, 7)));
		programar(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 5, 29));
		programar(LocalDate.of(2026, 6, 8), LocalDate.of(2026, 9, 4));
		programar(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 12, 11));

		assertThat(jdbc.queryForList("SELECT nombre FROM periodo_evaluacion WHERE anio_escolar_id = ? ORDER BY numero",
				String.class, escuela.anio2026())).containsExactly("I trimestre", "II trimestre", "III trimestre");
		assertThatThrownBy(() -> programar(LocalDate.of(2026, 12, 14), LocalDate.of(2026, 12, 18)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("3 periodos");
	}

	@Test
	void debeProgramarDosSemestresYRechazarElTercero() {
		como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.SEMESTRE, 7)));
		programar(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 7, 17));
		programar(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 12, 11));

		assertThat(jdbc.queryForList("SELECT nombre FROM periodo_evaluacion WHERE anio_escolar_id = ? ORDER BY numero",
				String.class, escuela.anio2026())).containsExactly("I semestre", "II semestre");
		assertThatThrownBy(() -> programar(LocalDate.of(2026, 12, 14), LocalDate.of(2026, 12, 18)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("2 periodos");
	}

	/** E11: con el I trimestre abierto, Dirección ya no cambia a bimestres (el tipo queda fijo). */
	@Test
	void debeRechazarCambiarDeTrimestreABimestreConUnPeriodoAbierto() {
		como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.TRIMESTRE, 7)));
		Long primero = programar(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 5, 29));
		hoyEs(LocalDate.of(2026, 3, 2));
		como(EscenarioCobranza.DIRECCION, () -> periodos.abrir(primero));

		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> configuraciones.guardar(escuela.anio2026(),
				valores(TipoPeriodo.BIMESTRE, 7)))).isInstanceOf(ReglaNegocioException.class);
		assertThat(jdbc.queryForObject("SELECT tipo_periodo FROM configuracion_evaluacion WHERE anio_escolar_id = ?",
				String.class, escuela.anio2026())).isEqualTo("TRIMESTRE");
	}

	private Long programar(LocalDate inicio, LocalDate fin) {
		return como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(), inicio, fin));
	}
}
