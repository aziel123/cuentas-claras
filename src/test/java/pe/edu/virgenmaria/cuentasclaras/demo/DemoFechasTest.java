package pe.edu.virgenmaria.cuentasclaras.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.cobranza.inicial.PensionesDemo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.demo.dto.DemoCreada;
import pe.edu.virgenmaria.cuentasclaras.demo.service.DemoColegios;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 8, tanda 3 (sección 8.2): la demo se crea bien en cualquier fecha del año (el año escolar, el «cobrar desde» y el
 * saldo inicial salen de la fecha de hoy): en enero (antes de clases: sin saldo inicial), en marzo, en octubre y en
 * diciembre. Las fechas van en orden (la bitácora no retrocede).
 */
@SpringBootTest
@ActiveProfiles({ "test", "demo" })
@Import(ConfiguracionRelojAjustable.class)
class DemoFechasTest {

	@Autowired
	private DemoColegios demos;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void seCreaEnCualquierFechaDelAnio() {
		crearEl("2026-03-10T15:00:00Z", 2026, false);
		crearEl("2026-10-02T13:00:00Z", 2026, true);
		crearEl("2026-12-20T15:00:00Z", 2026, true);
		crearEl("2027-01-15T15:00:00Z", 2027, false);
	}

	@Test
	void cobraDesdeElMesDeHoyYEnEneroDesdeMarzo() {
		assertThat(PensionesDemo.cobroDesde(LocalDate.of(2027, 1, 15))).isEqualTo(LocalDate.of(2027, 3, 1));
		assertThat(PensionesDemo.cobroDesde(LocalDate.of(2026, 10, 12))).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(PensionesDemo.cobroDesde(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2026, 12, 1));
	}

	private void crearEl(String instante, int anio, boolean conSaldoInicial) {
		reloj.fijar(Instant.parse(instante));
		DemoCreada demo = demos.crear("192.0.2." + instante.substring(5, 7));
		long colegio = jdbc.queryForObject("SELECT colegio_id FROM usuario WHERE nombre_usuario = ?", Long.class,
				demo.cuentas().getFirst().usuario());
		assertThat(jdbc.queryForObject("SELECT anio FROM anio_escolar WHERE colegio_id = ?", Integer.class, colegio))
				.as(instante).isEqualTo(anio);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE colegio_id = ?", Long.class, colegio)).as(instante)
				.isEqualTo(4L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lote_saldo_inicial WHERE colegio_id = ?", Long.class, colegio))
				.as(instante).isEqualTo(conSaldoInicial ? 1L : 0L);
	}
}
