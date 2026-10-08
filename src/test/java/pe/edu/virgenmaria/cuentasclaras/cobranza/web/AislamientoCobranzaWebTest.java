package pe.edu.virgenmaria.cuentasclaras.cobranza.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LineaSaldoRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.LoteRequest;
import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConceptoSaldo;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioSaldoInicial;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/** La Dirección del colegio B no ve ni aprueba planes, cronogramas ni lotes del colegio A. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoCobranzaWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioSaldoInicial saldo;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	private Estructura escuelaA;

	private Long mateoA;

	private Long borradorA;

	private Long loteA;

	private UsuarioAutenticado directorB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuelaA = EscenarioEscolar.crearEstructura(estructura);
		mateoA = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuelaA.primaria6A2027())).alumnoId();
		EscenarioCobranza.planAprobado(planes, escuelaA.anio2027(), 2027, Nivel.PRIMARIA, "450", "350", null);
		borradorA = planes.crearBorrador(escuelaA.anio2027(), Nivel.SECUNDARIA, EscenarioCobranza.plan(2027, "480", "350",
				null));
		loteA = saldo.crearLote(new LoteRequest(escuelaA.anio2027(), LocalDate.of(2026, 9, 30), "Informe A",
				new BigDecimal("450.00")));
		saldo.agregarLinea(loteA, new LineaSaldoRequest(EscenarioEscolar.DNI_MATEO, ConceptoSaldo.OTRO, null, null,
				"Taller de verano", new BigDecimal("450.00"), LocalDate.of(2026, 9, 1)));
		saldo.enviar(loteA);

		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		directorB = UsuariosDePrueba.autenticado(colegioB, 99L, "director.b", "Directora B", false,
				EnumSet.of(Rol.DIRECTOR));
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void colegioBNoVePlanesCuotasNiLotesDelA() throws Exception {
		mvc.perform(get("/pensiones").with(UsuariosDePrueba.como(directorB))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Todavía no hay años escolares")))
				.andExpect(content().string(not(containsString("Plan Primaria 2027"))));
		mvc.perform(get("/pensiones/saldo-inicial").with(UsuariosDePrueba.como(directorB))).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Informe A"))));
		mvc.perform(get("/pensiones/cronogramas").with(UsuariosDePrueba.como(directorB))).andExpect(status().isOk());
		mvc.perform(get("/pensiones").param("anio", escuelaA.anio2027().toString()).with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isNotFound());
	}

	@Test
	void directorDelColegioBRecibe404AlAprobarPlanDelA() throws Exception {
		mvc.perform(get("/pensiones/planes/" + borradorA).with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isNotFound());
		mvc.perform(post("/pensiones/planes/" + borradorA + "/aprobar").with(UsuariosDePrueba.como(directorB)).with(csrf()))
				.andExpect(status().isNotFound());
		mvc.perform(post("/pensiones/cronogramas/generar").param("anio", escuelaA.anio2027().toString())
				.with(UsuariosDePrueba.como(directorB)).with(csrf())).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT estado FROM plan_pension WHERE id = ?", String.class, borradorA))
				.isEqualTo("BORRADOR");
	}

	@Test
	void directorDelColegioBRecibe404AlConfirmarLoteDelA() throws Exception {
		mvc.perform(get("/pensiones/saldo-inicial/" + loteA).with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isNotFound());
		mvc.perform(post("/pensiones/saldo-inicial/" + loteA + "/confirmar").with(UsuariosDePrueba.como(directorB))
				.with(csrf())).andExpect(status().isNotFound());
		mvc.perform(post("/pensiones/saldo-inicial/" + loteA + "/devolver").with(UsuariosDePrueba.como(directorB))
				.with(csrf()).param("motivo", "Intento desde otro colegio")).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT estado FROM lote_saldo_inicial WHERE id = ?", String.class, loteA))
				.isEqualTo("ENVIADO");
	}

	@Test
	void cronogramaDeAlumnoDelARecibe404DesdeB() throws Exception {
		mvc.perform(get("/alumnos/" + mateoA + "/cronograma").with(UsuariosDePrueba.como(directorB)))
				.andExpect(status().isNotFound())
				.andExpect(content().string(not(containsString("Quispe"))))
				.andExpect(content().string(not(containsString("S/ 450.00"))));
	}
}
