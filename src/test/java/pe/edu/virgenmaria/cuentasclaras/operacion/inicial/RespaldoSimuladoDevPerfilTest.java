package pe.edu.virgenmaria.cuentasclaras.operacion.inicial;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.RespaldoRepository;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * El respaldo simulado de la demo: solo existe con el perfil dev (nunca en prod, piloto, demo ni pruebas) y, sobre la base
 * H2 con los CHECK de V24, deja «Hecho» el paso «Respaldo» de la Puesta en marcha.
 */
@PruebaIntegracion
class RespaldoSimuladoDevPerfilTest {

	@Autowired
	private ApplicationContext contexto;

	@Autowired
	private EntityManager entidades;

	@Autowired
	private RespaldoRepository respaldos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private PropiedadesMonitoreo propiedades;

	@Autowired
	private ObjectProvider<Flyway> flyway;

	@Autowired
	private Clock reloj;

	@Autowired
	private EstadoPuestaEnMarcha puestaEnMarcha;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void limpiar() {
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void soloEsUnBeanConElPerfilDev() {
		assertThat(contexto.getBeanNamesForType(RespaldoSimuladoDev.class)).isEmpty();
		for (String perfil : new String[] { "prod", "piloto", "demo", "test" }) {
			new ApplicationContextRunner().withInitializer(c -> c.getEnvironment().setActiveProfiles(perfil))
					.withUserConfiguration(RespaldoSimuladoDev.class)
					.run(c -> assertThat(c).as(perfil).hasNotFailed().doesNotHaveBean(RespaldoSimuladoDev.class));
		}
		new ApplicationContextRunner().withInitializer(c -> c.getEnvironment().setActiveProfiles("dev"))
				.withBean(EntityManager.class, () -> mock(EntityManager.class))
				.withBean(RespaldoRepository.class, () -> mock(RespaldoRepository.class))
				.withBean(TransactionTemplate.class, () -> new TransactionTemplate(mock(PlatformTransactionManager.class)))
				.withBean(PropiedadesMonitoreo.class, () -> propiedades).withBean(Clock.class, () -> reloj)
				.withUserConfiguration(RespaldoSimuladoDev.class)
				.run(c -> assertThat(c).hasSingleBean(RespaldoSimuladoDev.class));
	}

	@Test
	void elRespaldoSimuladoDejaHechoElPasoRespaldoYNoSeDuplica() {
		assertThat(paso()).isEqualTo(PuntoLista.Estado.PENDIENTE);
		RespaldoSimuladoDev demo = new RespaldoSimuladoDev(entidades, respaldos, new TransactionTemplate(transacciones),
				propiedades, flyway, reloj);

		assertThat(demo.registrarSiHaceFalta()).isPresent();
		assertThat(demo.registrarSiHaceFalta()).isEmpty();

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM respaldo WHERE destino = 'simulado' AND creado_por = "
				+ "'cc_respaldo' AND comparacion = 'PRIMERO'", Integer.class)).isEqualTo(1);
		assertThat(paso()).isEqualTo(PuntoLista.Estado.HECHO);
	}

	private PuntoLista.Estado paso() {
		return puestaEnMarcha.paraOperador(1L).puntos().stream()
				.filter(p -> EstadoPuestaEnMarcha.RESPALDO.equals(p.clave())).findFirst().orElseThrow().estado();
	}
}
