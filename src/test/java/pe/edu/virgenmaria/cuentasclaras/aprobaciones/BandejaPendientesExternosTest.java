package pe.edu.virgenmaria.cuentasclaras.aprobaciones;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.api.PendienteExterno;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaExterna;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.web.ContadorAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.PROMOTORIA;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.plan;

/**
 * Sprint 11, tanda 1b (F-D5, decisión 209): la bandeja de Aprobaciones lista y cuenta lo que se aprueba en otra pantalla,
 * con «Revisar» a esa pantalla y la regla de {@code puedeResolver}. Un caso por tipo que se puede armar en H2 (plan,
 * lote de saldo inicial, feriado y correo de control) y la separación entre colegios. El contador es la suma.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class BandejaPendientesExternosTest {

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private BandejaExterna externa;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ContadorAprobaciones contador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private java.time.Clock reloj;

	private Estructura escuela;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		como(ADMINISTRACION);
		escuela = EscenarioEscolar.crearEstructura(estructura);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unPlanEnviadoPorAdministracionApareceEnLaBandejaDePromotoriaConRevisar() throws Exception {
		Long id = enviarPlan();

		como(PROMOTORIA);
		List<PendienteExterno> pendientes = externa.porResolver();
		assertThat(pendientes).singleElement().satisfies(p -> {
			assertThat(p.tipo()).isEqualTo("PLAN_PENSION");
			assertThat(p.titulo()).isEqualTo("Plan de pensiones de Primaria 2027");
			assertThat(p.enlace()).isEqualTo("/pensiones/planes/" + id);
			assertThat(p.detalle()).startsWith("Enviado por administracion el ");
		});
		assertThat(externa.contarPorResolver()).isEqualTo(1);
		assertThat(contador.contar()).isEqualTo(bandeja.contarPorResolver() + 1);

		mvc.perform(get("/aprobaciones").with(UsuariosDePrueba.como(PROMOTORIA))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Pendientes (1)")))
				.andExpect(content().string(containsString("aria-label=\"Revisar: Plan de pensiones de Primaria 2027\"")))
				.andExpect(content().string(containsString("href=\"/pensiones/planes/" + id + "\"")))
				.andExpect(content().string(containsString("Se revisa en su pantalla")));
		mvc.perform(get("/aprobaciones").param("vista", "movil").with(UsuariosDePrueba.como(PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Para aprobar: 1")))
				.andExpect(content().string(containsString("data-revisar")));
		// «Revisar» nunca lleva a una ruta con 403.
		assertThat(ModuloApp.puedeEntrar(java.util.EnumSet.of(pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR),
				"/pensiones/planes/" + id)).isTrue();
		mvc.perform(get("/pensiones/planes/" + id).with(UsuariosDePrueba.como(PROMOTORIA))).andExpect(status().isOk());

		// Después de aprobarlo, desaparece y el contador baja (MockMvc deja la sesión vacía: se vuelve a Promotoría).
		como(PROMOTORIA);
		planes.aprobar(id, planes.obtener(id).version());
		assertThat(externa.porResolver()).isEmpty();
		assertThat(contador.contar()).isEqualTo(bandeja.contarPorResolver());
	}

	@Test
	void quienLoEnvioNoLoCuentaYLoVeEnEnviadosPorTi() throws Exception {
		enviarPlan();
		// Quien lo editó y envió con Dirección (un usuario con los dos roles) no lo resuelve.
		como(pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION_Y_ADMINISTRACION);
		Long propio = planes.crearBorrador(escuela.anio2027(), Nivel.SECUNDARIA, plan(2027, "500", "400", null));
		planes.enviar(propio);
		assertThat(externa.porResolver()).extracting(PendienteExterno::enlace).doesNotContain("/pensiones/planes/" + propio);
		assertThat(externa.esperandoAOtraPersona()).extracting(PendienteExterno::enlace)
				.contains("/pensiones/planes/" + propio);
		mvc.perform(get("/aprobaciones").with(UsuariosDePrueba.como(
				pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.DIRECCION_Y_ADMINISTRACION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Enviados por ti, esperando a otra persona")));
	}

	@Test
	void unFeriadoPropuestoYElCorreoDeControlPropuestoTambienEntran() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		jdbc.update("INSERT INTO feriado (colegio_id, fecha, descripcion, vigente, pendiente, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, '2027-06-29', 'Día del Maestro (local)', TRUE, TRUE, ?, 'administracion', ?)",
				ahora, ahora);
		como(DIRECCION);
		assertThat(externa.porResolver()).extracting(PendienteExterno::tipo).contains("FERIADO");
		como(PROMOTORIA);
		assertThat(externa.porResolver()).filteredOn(p -> p.tipo().equals("FERIADO")).singleElement()
				.satisfies(p -> assertThat(p.enlace()).isEqualTo("/feriados"));
	}

	@Test
	void conDosColegiosNingunoVeLosPendientesDelOtro() {
		enviarPlan();
		como(UsuariosDePrueba.autenticado(2L, 99L, "promotor.b", "Promotor B", false,
				java.util.Set.of(pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR)));
		assertThat(externa.pendientes()).noneMatch(p -> p.tipo().equals("PLAN_PENSION"));
	}

	@Test
	void elContadorEsLaCantidadDePendientesDeLaBandeja() throws Exception {
		enviarPlan();
		for (var persona : List.of(PROMOTORIA, DIRECCION)) {
			como(persona);
			long esperados = bandeja.bandeja().pendientes().stream().filter(s -> s.puedeResolver()).count()
					+ externa.porResolver().size();
			assertThat(contador.contar()).as(persona.getUsername()).isEqualTo(esperados);
		}
	}

	private Long enviarPlan() {
		como(ADMINISTRACION);
		Long id = planes.crearBorrador(escuela.anio2027(), Nivel.PRIMARIA, plan(2027, "450", "350", null));
		planes.enviar(id);
		return id;
	}
}
