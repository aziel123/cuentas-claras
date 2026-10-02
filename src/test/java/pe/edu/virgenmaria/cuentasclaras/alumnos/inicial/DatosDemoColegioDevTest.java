package pe.edu.virgenmaria.cuentasclaras.alumnos.inicial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.RegistroAlumnos;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.AnioEscolarRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/** Los datos de demostración del colegio son {@code @Profile("dev")}: aquí se construyen a mano. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class DatosDemoColegioDevTest {

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private AnioEscolarRepository anios;

	@Autowired
	private SeccionRepository secciones;

	@Autowired
	private RegistroAlumnos registro;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private Clock reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private long colegioB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		colegioB = colegios.save(new Colegio(DatosDemoDev.NOMBRE_COLEGIO_B)).getId();
	}

	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void creaAniosSeccionesYFamiliasConHermanos() {
		assertThat(datosDemo("jdbc:h2:mem:demo", true).crearSiCorresponde()).isTrue();

		assertThat(jdbc.queryForList("SELECT CONCAT(anio, ':', estado) FROM anio_escolar WHERE colegio_id = 1 ORDER BY anio",
				String.class)).containsExactly("2026:EN_CURSO", "2027:PLANIFICADO");
		assertThat(contar("seccion WHERE colegio_id = 1")).isEqualTo(2 * (14 + 2));
		assertThat(contar("familia WHERE colegio_id = 1")).isEqualTo(7);
		assertThat(contar("alumno WHERE colegio_id = 1")).isEqualTo(10);
		assertThat(contar("matricula WHERE colegio_id = 1")).isEqualTo(10);
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT familia_id) FROM alumno WHERE apellido_paterno = 'Quispe'",
				Long.class)).isEqualTo(1);
		// Colegio B: su propio año y un alumno con el mismo DNI que Mateo (otra persona).
		assertThat(contar("anio_escolar WHERE colegio_id = " + colegioB)).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT colegio_id FROM alumno WHERE numero_documento = '78451236' ORDER BY colegio_id",
				Long.class)).containsExactly(1L, colegioB);
		assertThat(contar("evento_auditoria WHERE accion = 'MATRICULA_REGISTRADA'")).isEqualTo(11);
	}

	@Test
	void noRepiteSiYaHayAniosYSeNiegaFueraDeH2EnMemoriaOSiSeDesactiva() {
		assertThat(datosDemo("jdbc:mysql://localhost:3306/cuentasclaras", true).crearSiCorresponde()).isFalse();
		assertThat(datosDemo("jdbc:h2:mem:demo", false).crearSiCorresponde()).isFalse();
		assertThat(contar("anio_escolar")).isZero();

		datosDemo("jdbc:h2:mem:demo", true).crearSiCorresponde();
		assertThat(datosDemo("jdbc:h2:mem:demo", true).crearSiCorresponde()).isFalse();
		assertThat(contar("alumno WHERE colegio_id = 1")).isEqualTo(10);
	}

	private DatosDemoColegioDev datosDemo(String url, boolean habilitado) {
		return new DatosDemoColegioDev(colegios, anios, secciones, registro, auditoria, transacciones, reloj, url,
				habilitado);
	}

	private long contar(String desde) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + desde, Long.class);
	}
}
