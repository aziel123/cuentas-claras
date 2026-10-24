package pe.edu.virgenmaria.cuentasclaras.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.academico.inicial.LibretasDemo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.demo.dto.DemoCreada;
import pe.edu.virgenmaria.cuentasclaras.demo.service.DemoColegios;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 3 (decisión 187): la demo trae el I bimestre CERRADO con las notas de Matemática de 4.° Primaria A, las
 * libretas emitidas por sistema.libretas (también la del hijo de la cuenta «apoderado»), una versión 2 por la corrección
 * que aprobó Promotoría y el II bimestre ABIERTO. En marzo (sin un bimestre terminado) la demo sale igual, sin libretas.
 */
@SpringBootTest
@ActiveProfiles({ "test", "demo" })
@Import(ConfiguracionRelojAjustable.class)
class DemoLibretasTest {

	@Autowired
	private DemoColegios demos;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void enOctubreTraeLibretasConUnaVersion2YElIIBimestreAbierto() {
		reloj.fijar(Instant.parse("2026-10-21T14:00:00Z"));
		long colegio = colegioDe(demos.crear("192.0.2.80"));
		assertThat(jdbc.queryForList("SELECT estado FROM periodo_evaluacion WHERE colegio_id = ? ORDER BY numero",
				String.class, colegio)).containsExactly("CERRADO", "ABIERTO");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM calificacion WHERE colegio_id = ?", Long.class, colegio))
				.isPositive();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida WHERE colegio_id = ? AND numero_version = 1 AND "
				+ "creado_por = 'sistema.libretas'", Long.class, colegio)).isPositive();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida WHERE colegio_id = ? AND numero_version = 2 AND "
				+ "motivo = 'CORRECCION'", Long.class, colegio)).isEqualTo(1L);
		// La familia de la cuenta «apoderado» tiene su libreta por descargar.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida l JOIN alumno a ON a.id = l.alumno_id JOIN "
				+ "usuario u ON u.apoderado_id = a.responsable_pago_id WHERE l.colegio_id = ? AND u.nombre_usuario LIKE "
				+ "'%.apoderado'", Long.class, colegio)).isPositive();
	}

	@Test
	void enMarzoSaleSinLibretas() {
		reloj.fijar(Instant.parse("2026-03-10T15:00:00Z"));
		long colegio = colegioDe(demos.crear("192.0.2.81"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida WHERE colegio_id = ?", Long.class, colegio))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE colegio_id = ?", Long.class, colegio)).isEqualTo(4L);
	}

	@Test
	void lasFechasDeLosBimestres() {
		LocalDate inicio = LocalDate.of(2026, 3, 9);
		LocalDate fin = LocalDate.of(2026, 12, 18);
		assertThat(LibretasDemo.bimestres(inicio, fin,
				LocalDate.of(2026, 3, 20))).isNull();
		assertThat(LibretasDemo.bimestres(inicio, fin,
				LocalDate.of(2026, 10, 21))).isNotNull();
		assertThat(LibretasDemo.bimestres(inicio, fin,
				LocalDate.of(2026, 12, 30))).isNull();
	}

	private long colegioDe(DemoCreada demo) {
		return jdbc.queryForObject("SELECT colegio_id FROM usuario WHERE nombre_usuario = ?", Long.class,
				demo.cuentas().getFirst().usuario());
	}
}
