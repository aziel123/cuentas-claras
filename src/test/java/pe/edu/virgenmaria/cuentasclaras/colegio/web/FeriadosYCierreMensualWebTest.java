package pe.edu.virgenmaria.cuentasclaras.colegio.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioConciliacion.Extracto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.proceso.CierresMensuales;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCuentasBancarias;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioExtractos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.LocalDate;
import java.util.EnumSet;

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
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.CAJA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 5, tanda 3: pantallas 11 (feriados) y 12 (cierre mensual) y la preferencia de recordatorios del portal. La
 * matriz de la sección 11: CAJA solo ve los feriados; Administración no registra feriados ni hace el cierre; mientras el
 * cierre está abierto la página no trae los totales; el apoderado solo cambia SUS recordatorios.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class FeriadosYCierreMensualWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioExtractos extractos;

	@Autowired
	private ServicioCuentasBancarias cuentas;

	@Autowired
	private CierresMensuales cierres;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private CalendarioHabil calendario;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		calendario.invalidarTodo();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
		calendario.invalidarTodo();
	}

	@Test
	void cajaVeLosFeriadosPeroNoLosRegistra() throws Exception {
		mvc.perform(get("/feriados").with(UsuariosDePrueba.como(CAJA))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Combate de Angamos")))
				.andExpect(content().string(not(containsString("Registrar día no laborable"))));
		mvc.perform(post("/feriados").with(csrf()).with(UsuariosDePrueba.como(CAJA)).param("fecha", "2026-10-09")
				.param("descripcion", "Día libre de la caja")).andExpect(status().isForbidden());
		mvc.perform(post("/feriados").with(csrf()).with(UsuariosDePrueba.como(ADMINISTRACION)).param("fecha", "2026-10-09")
				.param("descripcion", "Día libre de administración")).andExpect(status().isForbidden());
		assertThat(contar(jdbc, "feriado")).isZero();
	}

	@Test
	void direccionRegistraYAnulaDesdeLaPantalla() throws Exception {
		mvc.perform(post("/feriados").with(csrf()).with(UsuariosDePrueba.como(DIRECCION)).param("fecha", "2026-10-09")
				.param("descripcion", "Aniversario del colegio")).andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("exito"));
		Long id = jdbc.queryForObject("SELECT id FROM feriado", Long.class);
		mvc.perform(get("/feriados").with(UsuariosDePrueba.como(DIRECCION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Aniversario del colegio")))
				.andExpect(content().string(containsString("Anular este día")));
		mvc.perform(post("/feriados/" + id + "/anular").with(csrf()).with(UsuariosDePrueba.como(DIRECCION))
				.param("motivo", "Se decidió trabajar ese día")).andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT anulado_por FROM feriado WHERE id = ?", String.class, id))
				.isEqualTo("director");
	}

	@Test
	void elCierreAbiertoNoMuestraLosTotalesYAdministracionNoEntra() throws Exception {
		Long cuentaId = EscenarioConciliacion.cuenta(cuentas);
		Extracto banco = EscenarioConciliacion.extracto("10000.00")
				.abono("2026-09-01", "YAPE RECIBIDO", "YP1001", "812.34")
				.abono("2026-09-30", "TRANSFERENCIA DE TERCEROS", "TR2002", "1000.00");
		EscenarioConciliacion.registrar(extractos, ADMINISTRACION, banco);
		EscenarioConciliacion.confirmar(extractos, PROMOTORIA, cuentaId, banco.saldoFinal().toPlainString());
		SecurityContextHolder.clearContext();
		cierres.enColegio(1L, LocalDate.of(2026, 10, 2));
		Long id = jdbc.queryForObject("SELECT id FROM cierre_mensual_banco", Long.class);

		mvc.perform(get("/conciliacion/cierres-mensuales/" + id).with(UsuariosDePrueba.como(DIRECCION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Comprobar el cierre")))
				.andExpect(content().string(not(containsString("1,812.34"))))
				.andExpect(content().string(not(containsString("11,812.34"))));
		mvc.perform(get("/conciliacion/cierres-mensuales/" + id).with(UsuariosDePrueba.como(ADMINISTRACION)))
				.andExpect(status().isForbidden());
		mvc.perform(post("/conciliacion/cierres-mensuales/" + id).with(csrf()).with(UsuariosDePrueba.como(DIRECCION))
				.param("version", "0").param("abonos", "1812.34").param("cargos", "0").param("saldo", "11812.34"))
				.andExpect(flash().attributeExists("exito"));
		mvc.perform(get("/conciliacion/cierres-mensuales").with(UsuariosDePrueba.como(PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Cuadrado")));
	}

	@Test
	void elApoderadoApagaSusRecordatoriosDesdeElPortal() throws Exception {
		EscenarioCaja.Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		UsuarioAutenticado rosa = new UsuarioAutenticado(70L, 1L, "rosa", "rosa", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());

		mvc.perform(get("/familia").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Apagar recordatorios")));
		mvc.perform(post("/familia/preferencias").with(csrf()).with(UsuariosDePrueba.como(rosa))
				.param("recordatorios", "false")).andExpect(status().is3xxRedirection());

		assertThat(jdbc.queryForObject("SELECT recordatorios_activos FROM apoderado WHERE id = ?", Boolean.class,
				f.rosa())).isFalse();
		assertThat(jdbc.queryForObject("SELECT recordatorios_activos FROM apoderado WHERE id = ?", Boolean.class,
				f.pedro())).isTrue();
		assertThat(contar(jdbc, "evento_auditoria WHERE accion = 'RECORDATORIOS_DESACTIVADOS'")).isEqualTo(1);
		// El personal no entra a la preferencia del portal.
		mvc.perform(post("/familia/preferencias").with(csrf()).with(UsuariosDePrueba.como(PROMOTORIA))
				.param("recordatorios", "true")).andExpect(status().isForbidden());
	}
}
