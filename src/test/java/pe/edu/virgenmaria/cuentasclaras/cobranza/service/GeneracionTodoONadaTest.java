package pe.edu.virgenmaria.cuentasclaras.cobranza.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.Cuota;
import pe.edu.virgenmaria.cuentasclaras.cobranza.repository.CuotaRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * La generación escucha {@code MatriculaRegistrada} en la misma transacción: si falla, tampoco quedan la matrícula,
 * el alumno ni su familia (va aparte porque reemplaza el repositorio por un espía).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class GeneracionTodoONadaTest {

	@MockitoSpyBean
	private CuotaRepository cuotas;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private JdbcTemplate jdbc;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
	}

	@AfterEach
	void limpiar() {
		reset(cuotas);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void siFallaLaGeneracionNoQuedaLaMatricula() {
		EscenarioCobranza.planAprobado(planes, escuela.anio2027(), 2027,
				Nivel.PRIMARIA, "450", "350", null);
		long eventosAntes = contar(jdbc, "evento_auditoria");
		doThrow(new IllegalStateException("Falla simulada al guardar la cuota")).when(cuotas).save(any(Cuota.class));

		assertThatThrownBy(() -> alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())))
				.hasMessageContaining("Falla simulada");

		assertThat(contar(jdbc, "matricula")).isZero();
		assertThat(contar(jdbc, "alumno")).isZero();
		assertThat(contar(jdbc, "familia")).isZero();
		assertThat(contar(jdbc, "cuota")).isZero();
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventosAntes);
	}
}
