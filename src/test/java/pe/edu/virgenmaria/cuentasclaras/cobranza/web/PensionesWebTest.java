package pe.edu.virgenmaria.cuentasclaras.cobranza.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/** Pantallas de pensiones de punta a punta (formulario, aprobación, cronograma, generación y saldo inicial). */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class PensionesWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private Estructura escuela;

	private Long mateo;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
		mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(escuela.primaria6A2027())).alumnoId();
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void limpiar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void administracionProponeConElFormularioYDireccionApruebaYSeVeElCronograma() throws Exception {
		mvc.perform(get("/pensiones").param("anio", escuela.anio2027().toString()).with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Proponer plan de Primaria")))
				.andExpect(content().string(containsString("Sin plan aprobado")));
		mvc.perform(get("/pensiones/planes/nuevo").param("anio", escuela.anio2027().toString()).param("nivel", "PRIMARIA")
						.with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"vencimientos[0]\"")))
				.andExpect(content().string(containsString("value=\"2027-09-30\"")))
				.andExpect(content().string(containsString("name=\"vencimientos[11]\"")))
				.andExpect(content().string(containsString("value=\"2027-02-28\"")));

		MvcResult creado = mvc.perform(planValido(post("/pensiones/planes/nuevo"), "450.00")
						.with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("exito", containsString("propusiste el plan")))
				.andReturn();
		String ruta = creado.getResponse().getRedirectedUrl();
		assertThat(jdbc.queryForObject("SELECT vencimientos_pension FROM plan_pension", String.class))
				.isEqualTo("2027-03-31,2027-04-30,2027-05-31,2027-06-30,2027-07-31,2027-08-31,2027-09-30,2027-10-31,"
						+ "2027-11-30,2027-12-31");

		// Quien lo propuso ve el plan, pero no el botón de aprobar.
		mvc.perform(get(ruta).with(UsuariosDePrueba.como(ADMINISTRACION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("setiembre")))
				.andExpect(content().string(not(containsString("Aprobar plan"))));
		mvc.perform(get(ruta).with(UsuariosDePrueba.como(DIRECCION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Aprobar plan")))
				.andExpect(content().string(containsString("S/ 450.00")));
		mvc.perform(post(ruta + "/aprobar").with(UsuariosDePrueba.como(DIRECCION)).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("exito", "Listo: aprobaste el plan. Se generaron 11 cuotas para 1 matrículas "
						+ "(S/ 4,850.00)."));

		mvc.perform(get("/alumnos/" + mateo + "/cronograma").with(UsuariosDePrueba.como(PROMOTORIA)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Pensión setiembre 2027")))
				.andExpect(content().string(containsString("Matrícula 2027")))
				.andExpect(content().string(containsString("S/ 4,850.00")))
				.andExpect(content().string(containsString("Plan Primaria 2027 v1")))
				.andExpect(content().string(containsString("data-estado=\"PENDIENTE\"")))
				.andExpect(content().string(not(containsString("data-estado=\"VENCIDA\""))))
				.andExpect(content().string(containsString("aria-current=\"page\">Cronograma")));
		mvc.perform(get("/alumnos/" + mateo).with(UsuariosDePrueba.como(PROMOTORIA)))
				.andExpect(content().string(containsString("/alumnos/" + mateo + "/cronograma")));
	}

	@Test
	void vencidaSeCalculaConLaFechaDeLima() throws Exception {
		crearYAprobar("450.00");
		// 01/10/2027 a las 00:30 de Lima (05:30 UTC): la pensión de setiembre (30/09) ya venció; la de octubre no.
		((RelojAjustable) reloj).fijar(Instant.parse("2027-10-01T05:30:00Z"));

		String html = mvc.perform(get("/alumnos/" + mateo + "/cronograma").with(UsuariosDePrueba.como(DIRECCION)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(html.split("data-estado=\"VENCIDA\"", -1)).hasSize(9);
		assertThat(html).contains("Vencido al 01/10/2027").contains("S/ 3,500.00");
		assertThat(jdbc.queryForList("SELECT DISTINCT estado FROM cuota", String.class)).containsExactly("PENDIENTE");
	}

	@Test
	void montoConComaMuestraUnErrorClaroYNoGuardaNada() throws Exception {
		mvc.perform(planValido(post("/pensiones/planes/nuevo"), "1,250.00").with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("sin comas ni «S/»")))
				// Lo que escribió se conserva para corregirlo.
				.andExpect(content().string(containsString("value=\"2027-09-30\"")))
				.andExpect(content().string(containsString("value=\"1,250.00\"")));
		mvc.perform(planValido(post("/pensiones/planes/nuevo"), "450.005").with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("como máximo 2 decimales")));
		mvc.perform(planValido(post("/pensiones/planes/nuevo"), "300.00").param("montoMatricula", "350.00")
						.with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("DS 005-2021-MINEDU")));
		assertThat(contar(jdbc, "plan_pension")).isZero();
	}

	@Test
	void generarPendientesDosVecesNoDuplica() throws Exception {
		crearYAprobar("450.00");
		long cuotas = contar(jdbc, "cuota");

		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/pensiones/cronogramas/generar").param("anio", escuela.anio2027().toString())
							.with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf()))
					.andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("exito", "No había cronogramas pendientes: no se generó nada."));
		}
		assertThat(contar(jdbc, "cuota")).isEqualTo(cuotas);
		mvc.perform(get("/pensiones/cronogramas").param("anio", escuela.anio2027().toString())
						.with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("sin cronograma: 0")))
				.andExpect(content().string(containsString("Generar pendientes")));
	}

	@Test
	void loteQueNoCuadraNoSeEnviaYElQueCuadraLoConfirmaPromotoria() throws Exception {
		MvcResult creado = mvc.perform(post("/pensiones/saldo-inicial").with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf())
						.param("anioId", escuela.anio2026().toString()).param("fechaCorte", "2026-09-30")
						.param("documentoReferencia", "Informe CPC 014-2026").param("totalDeclarado", "500.00"))
				.andExpect(status().is3xxRedirection()).andReturn();
		String lote = creado.getResponse().getRedirectedUrl();
		mvc.perform(post(lote + "/lineas").with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf())
						.param("documentoAlumno", EscenarioEscolar.DNI_MATEO).param("concepto", "PENSION").param("mes", "9")
						.param("monto", "450.00"))
				.andExpect(status().is3xxRedirection());

		mvc.perform(get(lote).with(UsuariosDePrueba.como(ADMINISTRACION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("No cuadra")))
				.andExpect(content().string(containsString("Pensión setiembre 2026")))
				.andExpect(content().string(not(containsString("Enviar para confirmación"))));
		mvc.perform(post(lote + "/enviar").with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf()))
				.andExpect(flash().attribute("error", containsString("No cuadra")));

		mvc.perform(post(lote + "/lineas").with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf())
						.param("documentoAlumno", EscenarioEscolar.DNI_MATEO).param("concepto", "OTRO")
						.param("descripcion", "Taller de verano 2026").param("monto", "50.00").param("vencimiento", "2026-02-15"))
				.andExpect(status().is3xxRedirection());
		mvc.perform(post(lote + "/enviar").with(UsuariosDePrueba.como(ADMINISTRACION)).with(csrf()))
				.andExpect(flash().attribute("exito", containsString("enviaste el lote")));
		mvc.perform(get(lote).with(UsuariosDePrueba.como(PROMOTORIA))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Cuadra")))
				.andExpect(content().string(containsString("Confirmar lote")));
		mvc.perform(post(lote + "/confirmar").with(UsuariosDePrueba.como(PROMOTORIA)).with(csrf()))
				.andExpect(flash().attribute("exito", containsString("Se crearon 2 cuotas")));

		assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM cuota WHERE tipo = 'SALDO_INICIAL'", BigDecimal.class))
				.isEqualByComparingTo("500.00");
		mvc.perform(get("/alumnos/" + mateo + "/cronograma").with(UsuariosDePrueba.como(PROMOTORIA)))
				.andExpect(content().string(containsString("confirmado por promotor")));
	}

	private void crearYAprobar(String pension) throws Exception {
		MvcResult creado = mvc.perform(planValido(post("/pensiones/planes/nuevo"), pension)
				.with(UsuariosDePrueba.como(ADMINISTRACION))).andReturn();
		mvc.perform(post(creado.getResponse().getRedirectedUrl() + "/aprobar").with(UsuariosDePrueba.como(DIRECCION))
				.with(csrf())).andExpect(status().is3xxRedirection());
	}

	private MockHttpServletRequestBuilder planValido(MockHttpServletRequestBuilder peticion, String pension) {
		peticion.with(csrf()).param("anio", escuela.anio2027().toString()).param("nivel", "PRIMARIA")
				.param("montoMatricula", "350.00").param("vencimientoMatricula", "2027-02-28")
				.param("montoPension", pension).param("cobroDesde", "");
		String[] meses = { "03-31", "04-30", "05-31", "06-30", "07-31", "08-31", "09-30", "10-31", "11-30", "12-31", "", "" };
		for (int i = 0; i < meses.length; i++) {
			peticion.param("vencimientos[" + i + "]", meses[i].isEmpty() ? "" : "2027-" + meses[i]);
		}
		return peticion;
	}
}
