package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.CronogramaAlumno;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.EstadoVisibleCuota;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/** El cronograma calcula VENCIDA con el reloj de la aplicación en hora de Lima (no con la zona de la JVM). */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioCronogramaTest {

	@Autowired
	private ServicioCronograma cronogramas;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private Long mateo;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		Estructura escuela = EscenarioEscolar.crearEstructura(estructura);
		mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		EscenarioCobranza.planAprobado(planes, escuela.anio2027(), 2027, Nivel.PRIMARIA, "450", "350", null);
	}

	@AfterEach
	void limpiar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** QA (mutación «reloj UTC»): a las 23:59:59 de Lima del 31/03 marzo aún no vence; a las 00:00 sí. */
	@Test
	void cronogramaUsaLaFechaDeLimaParaElVencido() {
		((RelojAjustable) reloj).fijar(Instant.parse("2027-04-01T04:59:59Z"));
		CronogramaAlumno antes = cronogramas.deAlumno(mateo);
		assertThat(antes.hoy()).isEqualTo(LocalDate.of(2027, 3, 31));
		assertThat(estadoDeMarzo(antes)).isEqualTo(EstadoVisibleCuota.PENDIENTE);
		assertThat(antes.vencido()).isEqualByComparingTo("350.00");

		((RelojAjustable) reloj).fijar(Instant.parse("2027-04-01T05:00:00Z"));
		CronogramaAlumno despues = cronogramas.deAlumno(mateo);
		assertThat(estadoDeMarzo(despues)).isEqualTo(EstadoVisibleCuota.VENCIDA);
		assertThat(despues.vencido()).isEqualByComparingTo("800.00");
	}

	private static EstadoVisibleCuota estadoDeMarzo(CronogramaAlumno cronograma) {
		return cronograma.cuotas().stream().filter(c -> c.descripcion().equals("Pensión marzo 2027")).findFirst()
				.orElseThrow().estado();
	}
}
