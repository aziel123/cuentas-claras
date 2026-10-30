package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.plataforma.proceso.RevisionConservacion;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (3.11): {@code RevisionConservacion} avisa al colegio y a los operadores 30 días antes de que venza la
 * conservación y el día que vence; los demás días, nada. Nunca suprime.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RevisionConservacionTest extends BaseFinColegio {

	@Autowired
	private RevisionConservacion revision;

	@Test
	void avisa30DiasAntesYElDiaQueVence() {
		long colegio = colegioEnProduccion("Colegio Vence", EscenarioPlataforma.RUC_C, "titular.vence", "45678993",
				"987650093");
		suspender(colegio);
		recibir(colegio, "45678993");
		cerrar(colegio);
		LocalDate hasta = jdbc.queryForObject("SELECT conservar_hasta FROM colegio WHERE id = ?", java.sql.Date.class,
				colegio).toLocalDate();

		en(hasta.minusDays(31));
		assertThat(revision.revisar(colegio)).isEmpty();
		en(hasta.minusDays(30));
		assertThat(revision.revisar(colegio)).singleElement().asString().contains("30 días");
		en(hasta.minusDays(29));
		assertThat(revision.revisar(colegio)).isEmpty();
		en(hasta);
		assertThat(revision.revisar(colegio)).singleElement().asString().contains("vence hoy");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE colegio_id = ? AND accion = "
				+ "'CONSERVACION_POR_VENCER'", Long.class, colegio)).isEqualTo(2L);
		assertThat(estado(colegio)).isEqualTo("CERRADO");
	}

	private void en(LocalDate dia) {
		reloj.fijar(dia.atTime(6, 0).atZone(ZoneId.of("America/Lima")).toInstant());
	}
}
