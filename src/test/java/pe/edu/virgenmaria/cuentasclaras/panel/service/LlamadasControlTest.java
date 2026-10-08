package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadaRequest;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadasSemana;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.AvisosPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;

/**
 * Sprint 6, tanda 3 (decisión 77, P17): la llamada de control semanal. Sobre {@link EscenarioPanel} (el jueves 15/04/2027
 * la familia Quispe pagó en efectivo, una vez anulado, y por Yape) más un pago en efectivo de la familia Flores ese día;
 * la semana de la llamada es la del lunes 19/04/2027 (los pagos del 15/04 caen en los 35 días anteriores).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class LlamadasControlTest {

	private static final LocalDate LUNES = LocalDate.of(2027, 4, 19);

	private static final String NOTA = "Dice que pagó S/ 450.00 en efectivo el 15/04 y no aparece";

	@Autowired
	private LlamadasControl llamadas;

	@Autowired
	private AlertasPanel alertasPanel;

	@Autowired
	private PanelPromotoria panel;

	@Autowired
	private AvisosPromotoria avisos;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioPanel.Datos datos;

	private Usuario promotora;

	private Usuario directora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(), "MAT-2027")),
				"350.00", "350.00"));
		SecurityContextHolder.clearContext();
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora", UsuariosDePrueba.CLAVE, false,
				Rol.DIRECTOR);
		a(LUNES.plusDays(1), 10, 0);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private void a(LocalDate dia, int hora, int minuto) {
		reloj.fijar(dia.atTime(hora, minuto).atZone(reloj.getZone()).toInstant());
	}

	private LlamadasSemana comoPromotora() {
		UsuariosDePrueba.iniciarSesion(promotora);
		return llamadas.deEstaSemana();
	}

	private long semillas() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM semilla_muestreo WHERE ambito = 'LLAMADA_CONTROL'", Long.class);
	}

	/** P17: la muestra sale de la semilla SECRETA de la semana (guardada, no deducible de la fecha) y de quien pagó en efectivo. */
	@Test
	void laMuestraUsaLaSemillaSecretaDeLaSemana() {
		LlamadasSemana semana = comoPromotora();

		assertThat(semana.semana()).isEqualTo("19/04/2027");
		assertThat(semana.desde()).isEqualTo("15/03/2027");
		assertThat(semana.familias()).extracting(LlamadasSemana.Familia::familiaId)
				.containsExactlyInAnyOrder(datos.f().quispe(), datos.f().flores());
		assertThat(semana.esperadas()).isEqualTo(2);
		assertThat(semana.faltan()).isEqualTo(2);
		assertThat(jdbc.queryForMap("SELECT fecha, creado_por FROM semilla_muestreo WHERE ambito = 'LLAMADA_CONTROL'"))
				.containsEntry("fecha", java.sql.Date.valueOf(LUNES));
		LlamadasSemana.Familia quispe = semana.familias().stream()
				.filter(f -> f.familiaId().equals(datos.f().quispe())).findFirst().orElseThrow();
		// El celular REGISTRADO de la responsable, y sus pagos de esas semanas (también el anulado y el de Yape) para
		// compararlos DESPUÉS de preguntar.
		assertThat(quispe.contactos()).extracting(LlamadasSemana.Contacto::celular)
				.anyMatch(c -> c != null && c.endsWith(EscenarioEscolar.CELULAR_ROSA));
		assertThat(quispe.pagos()).extracting(LlamadasSemana.Pago::monto)
				.containsExactlyInAnyOrder("S/ 350.00", "S/ 450.00", "S/ 350.00");
		assertThat(quispe.pagos()).filteredOn(LlamadasSemana.Pago::anulado).singleElement()
				.satisfies(p -> assertThat(p.estado()).isEqualTo("Anulado"));
		assertThat(quispe.sinPortal()).as("ningún apoderado entró al portal").isTrue();
		assertThat(quispe.pendiente()).isTrue();
	}

	/** P17: la muestra no cambia durante la semana (misma semilla) y la semana siguiente tiene su propia semilla. */
	@Test
	void esEstableDuranteLaSemana() {
		List<Long> martes = comoPromotora().familias().stream().map(LlamadasSemana.Familia::familiaId).toList();
		a(LUNES.plusDays(4), 18, 0);
		List<Long> viernes = comoPromotora().familias().stream().map(LlamadasSemana.Familia::familiaId).toList();
		assertThat(viernes).isEqualTo(martes);
		assertThat(semillas()).isEqualTo(1);

		a(LUNES.plusWeeks(1), 9, 0);
		assertThat(comoPromotora().semana()).isEqualTo("26/04/2027");
		assertThat(semillas()).as("una semilla por semana").isEqualTo(2);
	}

	/** P17: «No confirma» exige nota, queda resaltado en la bitácora, es CRÍTICA y sale al celular de Promotoría una vez. */
	@Test
	void noConfirmaEsCritica() {
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThatThrownBy(() -> llamadas.registrar(datos.f().quispe(),
				new LlamadaRequest(ResultadoLlamada.NO_CONFIRMA, "No sé")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("10 caracteres");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM llamada_control", Long.class)).isZero();

		Long id = llamadas.registrar(datos.f().quispe(), new LlamadaRequest(ResultadoLlamada.NO_CONFIRMA, NOTA));

		assertThat(jdbc.queryForMap("SELECT semana, resultado, nota, creado_por FROM llamada_control WHERE id = ?", id))
				.containsEntry("semana", java.sql.Date.valueOf(LUNES)).containsEntry("resultado", "NO_CONFIRMA")
				.containsEntry("nota", NOTA).containsEntry("creado_por", "promotora");
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "LLAMADA_CONTROL_NO_CONFIRMA");
		assertThat(evento).containsEntry("entidad", "llamada_control").containsEntry("entidad_id", id.toString())
				.containsEntry("valor_nuevo", "NO_CONFIRMA").containsEntry("nombre_usuario", "promotora");
		List<AlertaRevision> criticas = alertasPanel.alertas().stream()
				.filter(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA && a.aviso() != null
						&& a.aviso().tipo() == TipoAviso.LLAMADA_NO_CONFIRMA).toList();
		assertThat(criticas).singleElement().satisfies(a -> {
			assertThat(a.texto()).contains("no confirma lo registrado", NOTA);
			assertThat(a.enlace()).isEqualTo("/alumnos/familias/" + datos.f().quispe());
			assertThat(a.aviso().referencia()).isEqualTo("LC:" + id);
		});
		SecurityContextHolder.clearContext();

		avisos.enColegio(1L, LUNES.plusDays(1));
		avisos.enColegio(1L, LUNES.plusDays(1));

		List<Map<String, Object>> mensajes = jdbc.queryForList("SELECT usuario_id, parametros FROM mensaje WHERE "
				+ "tipo = 'ALERTA_PROMOTORIA' AND clave LIKE ?", "ALERTA:LLAMADA_NO_CONFIRMA:LC:" + id + ":U%");
		assertThat(mensajes).singleElement().satisfies(m -> {
			assertThat(m).containsEntry("usuario_id", promotora.getId());
			assertThat((String) m.get("parametros")).isEqualTo("Una familia no confirma lo registrado\n19/04/2027")
					.doesNotContain(NOTA, "Quispe");
		});
	}

	/**
	 * Lo que confirma o no contesta queda en la bitácora sin resaltar, con su valor nuevo; y no se registra dos veces.
	 * Correcciones del sprint 6 (S6-M2): Dirección registra solo porque Promotoría le delegó la semana.
	 */
	@Test
	void confirmaQuedaEnLaBitacoraYNoSeRegistraDosVeces() {
		UsuariosDePrueba.iniciarSesion(promotora);
		llamadas.delegarADireccion();
		UsuariosDePrueba.iniciarSesion(directora);
		Long id = llamadas.registrar(datos.f().flores(), new LlamadaRequest(ResultadoLlamada.CONFIRMA, null));

		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "LLAMADA_CONTROL_REGISTRADA"))
				.containsEntry("entidad_id", id.toString()).containsEntry("valor_nuevo", "CONFIRMA")
				.containsEntry("nombre_usuario", "directora");
		assertThatThrownBy(() -> llamadas.registrar(datos.f().flores(),
				new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Ya registraste");
		LlamadasSemana semana = llamadas.deEstaSemana();
		assertThat(semana.hechas()).isEqualTo(1);
		assertThat(semana.familias()).filteredOn(f -> f.familiaId().equals(datos.f().flores())).singleElement()
				.satisfies(f -> {
					assertThat(f.pendiente()).isFalse();
					assertThat(f.registrada().resultado()).isEqualTo("Confirma lo registrado");
					assertThat(f.registrada().por()).isEqualTo("directora");
				});
	}

	/**
	 * Solo una familia de la muestra de esta semana: otra (o una que no existe) es 404 y no deja nada. Correcciones del
	 * sprint 6 (S6-B3): la muestra quedó fija al consultarla; una familia nueva (con deuda vencida) no entra hasta la
	 * semana siguiente.
	 */
	@Test
	void unaFamiliaFueraDeLaMuestraNoSeRegistra() {
		comoPromotora();
		SecurityContextHolder.clearContext();
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long otra = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71234598", "Ríos", "Vega", "Ana",
				LocalDate.of(2016, 3, 3), "41234599", "Ríos", "Soto", "Luis", "923456781", null,
				jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE alumno_id = ?", Long.class,
						datos.f().mateo()))).familiaId();
		UsuariosDePrueba.iniciarSesion(promotora);
		for (Long familia : List.of(otra, 999_999L)) {
			assertThatThrownBy(() -> llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.CONFIRMA, null)))
					.isInstanceOf(RecursoNoEncontradoException.class);
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM llamada_control", Long.class)).isZero();
	}

	/** Administración, Caja y Docente no llaman ni ven la muestra (403 en el servicio y en la ruta). */
	@Test
	void soloPromotoriaYDireccionLlaman() throws Exception {
		for (Rol rol : new Rol[] { Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE, Rol.APODERADO }) {
			UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(rol));
			assertThatThrownBy(() -> llamadas.deEstaSemana()).as(rol.name())
					.isInstanceOf(AuthorizationDeniedException.class);
			SecurityContextHolder.clearContext();
			mvc.perform(get("/panel/llamadas").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
			mvc.perform(post("/panel/llamadas/" + datos.f().quispe()).with(csrf()).param("resultado", "CONFIRMA")
					.with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM llamada_control", Long.class)).isZero();
	}

	/** La pantalla: familias, celulares, pagos detrás de «Ya me dijo»; registrar es POST con CSRF. */
	@Test
	void laPantallaMuestraLaMuestraYRegistraConCsrf() throws Exception {
		mvc.perform(get("/panel/llamadas").with(UsuariosDePrueba.como(promotora))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Semana del 19/04/2027")))
				.andExpect(content().string(containsString("Ya me dijo: ver lo registrado")))
				.andExpect(content().string(containsString("Te faltan 2 de 2 esta semana.")))
				.andExpect(content().string(containsString("Volver al panel")));
		mvc.perform(get("/panel/llamadas").with(UsuariosDePrueba.como(directora))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Volver al inicio")));

		mvc.perform(post("/panel/llamadas/" + datos.f().flores()).param("resultado", "CONFIRMA")
				.with(UsuariosDePrueba.como(promotora))).andExpect(status().isForbidden());
		mvc.perform(get("/panel/llamadas/" + datos.f().flores()).with(UsuariosDePrueba.como(promotora)))
				.andExpect(status().isMethodNotAllowed());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM llamada_control", Long.class)).isZero();

		mvc.perform(post("/panel/llamadas/" + datos.f().flores()).with(csrf()).param("resultado", "NO_CONFIRMA")
				.param("nota", "corta").with(UsuariosDePrueba.como(promotora)))
				.andExpect(redirectedUrl("/panel/llamadas")).andExpect(flash().attributeExists("error"));
		mvc.perform(post("/panel/llamadas/" + datos.f().flores()).with(csrf()).param("resultado", "CONFIRMA")
				.with(UsuariosDePrueba.como(promotora)))
				.andExpect(redirectedUrl("/panel/llamadas")).andExpect(flash().attributeExists("exito"));
		assertThat(jdbc.queryForObject("SELECT resultado FROM llamada_control", String.class)).isEqualTo("CONFIRMA");
		mvc.perform(get("/panel/llamadas").with(UsuariosDePrueba.como(promotora))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Te faltan 1 de 2 esta semana.")))
				.andExpect(content().string(not(containsString("th:"))));
	}

	/** El panel dice cuántas faltan, y el sábado (y el domingo) las que faltan son ATENCIÓN. */
	@Test
	void elPanelDiceCuantasFaltanYElSabadoEsAtencion() {
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(panel.ver().llamadas().texto()).isEqualTo("Te faltan 2 de 2 esta semana.");
		assertThat(alertasPanel.alertas()).noneMatch(a -> a.texto().startsWith("Faltan "));

		a(LUNES.plusDays(5), 9, 0);
		assertThat(alertasPanel.alertas()).filteredOn(a -> a.texto().startsWith("Faltan ")).singleElement()
				.satisfies(a -> {
					assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.ATENCION);
					assertThat(a.texto()).contains("Faltan 2 de 2 llamadas de control");
					assertThat(a.aviso()).as("no sale al celular").isNull();
				});
		llamadas.registrar(datos.f().quispe(), new LlamadaRequest(ResultadoLlamada.CONFIRMA, null));
		llamadas.registrar(datos.f().flores(), new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));
		// S6-M2: «No contesta» no cierra la plaza; el segundo intento (una hora después) sí.
		assertThat(panel.ver().llamadas().texto()).isEqualTo("Te faltan 1 de 2 esta semana.");
		a(LUNES.plusDays(5), 10, 0);
		llamadas.registrar(datos.f().flores(), new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));
		assertThat(panel.ver().llamadas().texto()).isEqualTo("Hiciste las 2 de esta semana.");
		assertThat(alertasPanel.alertas()).noneMatch(a -> a.texto().startsWith("Faltan "));
	}
}
