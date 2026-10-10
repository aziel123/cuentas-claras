package pe.edu.virgenmaria.cuentasclaras.panel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadaRequest;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadasSemana;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.MuestraSemanal;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.ReemplazosLlamadas;
import pe.edu.virgenmaria.cuentasclaras.panel.service.FijacionMuestra;
import pe.edu.virgenmaria.cuentasclaras.panel.service.LlamadasControl;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;

/**
 * QA del sprint 7 (secciones 3.2, 3.6 y H3; E10, E13 y E17) sobre H2:
 * <ul>
 *   <li>todo proceso del sistema corre en su PROPIA transacción, aunque lo llame la transacción de una persona: lo que
 *       confirma el sistema no se revierte con la persona y al revés;</li>
 *   <li>una persona no hace lo que solo hace el sistema (fijar la muestra, mezclar identidad con negocio);</li>
 *   <li>la muestra de la llamada de control la fija {@code sistema.panel}, y el segundo «No contesta» (una hora después)
 *       la reemplaza después del commit o en la pasada de cada 15 minutos, una sola vez.</li>
 * </ul>
 * Una familia por semana (para que haya con quién reemplazar). Semana del lunes 19/04/2027 ({@link EscenarioPanel}).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@TestPropertySource(properties = "cuentasclaras.panel.llamadas-por-semana=1")
class ProcesosDelSistemaBordesTest {

	private static final LocalDate LUNES = LocalDate.of(2027, 4, 19);

	@Autowired
	private LlamadasControl llamadas;

	@Autowired
	private FijacionMuestra fijacion;

	@Autowired
	private MuestraSemanal muestraSemanal;

	@Autowired
	private ReemplazosLlamadas reemplazos;

	@Autowired
	private EjecucionIdentidad identidad;

	@Autowired
	private PlatformTransactionManager transacciones;

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

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		jdbc.update("DELETE FROM configuracion_bd WHERE clave LIKE 'qa_%'");
		datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
		// Los Flores también pagan en efectivo: dos candidatas para una plaza.
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(), "MAT-2027")),
				"350.00", "350.00"));
		SecurityContextHolder.clearContext();
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		jdbc.update("DELETE FROM configuracion_bd WHERE clave LIKE 'qa_%'");
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	// ------------------------------------------------------------------ transacción propia

	@Test
	void loQueConfirmaUnProcesoDelSistemaNoSeRevierteConLaTransaccionDeLaPersona() {
		UsuariosDePrueba.iniciarSesion(promotora);
		AtomicReference<RutaConexion> rutaDentro = new AtomicReference<>();
		AtomicReference<RutaConexion> rutaDespues = new AtomicReference<>();

		new TransactionTemplate(transacciones).executeWithoutResult(persona -> {
			jdbc.update("INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('qa_persona', 'x', ?)", ahora());
			EjecucionComoSistema.como(ActorSistema.PANEL, 1L, () -> {
				rutaDentro.set(RutaConexion.actual());
				jdbc.update("INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('qa_sistema', 'y', ?)", ahora());
			});
			rutaDespues.set(RutaConexion.actual());
			persona.setRollbackOnly();
		});

		assertThat(rutaDentro.get()).isEqualTo(RutaConexion.SISTEMA);
		assertThat(rutaDespues.get()).isEqualTo(RutaConexion.APP);
		assertThat(claves()).containsExactly("qa_sistema");
	}

	@Test
	void unProcesoDelSistemaQueFallaNoRevierteLoQueHizoLaPersona() {
		UsuariosDePrueba.iniciarSesion(promotora);

		new TransactionTemplate(transacciones).executeWithoutResult(persona -> {
			jdbc.update("INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('qa_persona', 'x', ?)", ahora());
			assertThatThrownBy(() -> EjecucionComoSistema.como(ActorSistema.PANEL, 1L, () -> {
				jdbc.update("INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('qa_sistema', 'y', ?)", ahora());
				throw new IllegalStateException("falla el proceso");
			})).isInstanceOf(IllegalStateException.class);
			assertThat(persona.isRollbackOnly()).as("la transacción de la persona sigue sana").isFalse();
		});

		assertThat(claves()).containsExactly("qa_persona");
		assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("promotora");
	}

	@Test
	void unProcesoNoCambiaDeColegioDentroDeLaTransaccionDeUnaPersona() {
		UsuariosDePrueba.iniciarSesion(promotora);

		new TransactionTemplate(transacciones).executeWithoutResult(persona -> assertThatThrownBy(
				() -> EjecucionComoSistema.como(ActorSistema.PANEL, 2L, () -> null))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("colegio"));
	}

	@Test
	void unaOperacionDeIdentidadNoSeMezclaConLaTransaccionDeUnaPersona() {
		UsuariosDePrueba.iniciarSesion(promotora);

		new TransactionTemplate(transacciones).executeWithoutResult(persona -> assertThatThrownBy(
				() -> identidad.como(() -> "nada")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cc_sistema"));
		assertThat(RutaConexion.enIdentidad()).isFalse();
	}

	// ------------------------------------------------------------------ lo que solo hace el sistema

	@Test
	void unaPersonaNoFijaNiReemplazaLaMuestraDirectamente() {
		a(LUNES, 9, 0);
		UsuariosDePrueba.iniciarSesion(promotora);

		new TransactionTemplate(transacciones).executeWithoutResult(persona -> {
			assertThatThrownBy(() -> fijacion.fijar(LUNES)).isInstanceOf(AccessDeniedException.class);
			assertThatThrownBy(() -> fijacion.reemplazar(LUNES, datos.f().quispe()))
					.isInstanceOf(AccessDeniedException.class);
			assertThatThrownBy(() -> fijacion.reemplazarPendientes(LUNES)).isInstanceOf(AccessDeniedException.class);
		});
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM muestra_llamada", Long.class)).isZero();
	}

	@Test
	void laMuestraLaFijaSistemaPanelAunqueLaPidaLaPantallaDeLaPromotora() {
		a(LUNES.plusDays(1), 10, 0);
		UsuariosDePrueba.iniciarSesion(promotora);

		LlamadasSemana semana = llamadas.deEstaSemana();

		assertThat(semana.familias()).hasSize(1);
		assertThat(jdbc.queryForList("SELECT CONCAT(creado_por, ' ', motivo) FROM muestra_llamada", String.class))
				.singleElement().satisfies(fila -> assertThat(fila).startsWith("sistema.panel "));
		assertThat(jdbc.queryForObject("SELECT creado_por FROM semilla_muestreo WHERE ambito = 'LLAMADA_CONTROL'",
				String.class)).isEqualTo("sistema.muestreo");
		assertThat(jdbc.queryForObject("SELECT fecha FROM semilla_muestreo WHERE ambito = 'LLAMADA_CONTROL'",
				LocalDate.class)).as("la semilla es la del lunes de la semana en curso").isEqualTo(LUNES);
		assertThat(jdbc.queryForObject("SELECT nombre_usuario FROM evento_auditoria WHERE accion = "
				+ "'MUESTRA_LLAMADAS_FIJADA'", String.class)).isEqualTo("sistema.panel");
	}

	@Test
	void laTareaDelLunesFijaLaMuestraUnaSolaVez() {
		a(LUNES, 0, 10);
		muestraSemanal.ejecutar();
		List<Map<String, Object>> primera = jdbc.queryForList("SELECT familia_id, creado_por FROM muestra_llamada");
		a(LUNES, 0, 25);
		muestraSemanal.ejecutar();

		assertThat(primera).singleElement().satisfies(f -> assertThat(f.get("creado_por")).isEqualTo("sistema.panel"));
		assertThat(jdbc.queryForList("SELECT familia_id, creado_por FROM muestra_llamada")).isEqualTo(primera);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'MUESTRA_LLAMADAS_FIJADA'",
				Long.class)).isEqualTo(1);
	}

	// ------------------------------------------------------------------ el segundo «No contesta»

	@Test
	void elSegundoNoContestaEsperaUnaHoraExacta() {
		Long familia = familiaDeLaMuestra();
		a(LUNES.plusDays(1), 10, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));

		a(LUNES.plusDays(1), 10, 59);
		assertThatThrownBy(() -> llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("11:00");
		a(LUNES.plusDays(1), 11, 0);
		llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));

		assertThat(jdbc.queryForList("SELECT intento FROM llamada_control WHERE familia_id = ? ORDER BY intento",
				Integer.class, familia)).containsExactly(1, 2);
	}

	@Test
	void alSegundoNoContestaElSistemaReemplazaALaFamiliaUnaSolaVez() {
		Long familia = familiaDeLaMuestra();
		Long otra = familia.equals(datos.f().quispe()) ? datos.f().flores() : datos.f().quispe();
		UsuariosDePrueba.iniciarSesion(promotora);
		a(LUNES.plusDays(1), 10, 0);
		llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));
		a(LUNES.plusDays(1), 12, 0);
		llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));

		// Después del commit, sistema.panel la reemplazó con la otra candidata.
		assertThat(jdbc.queryForMap("SELECT familia_id, reemplaza_familia_id, motivo, creado_por FROM muestra_llamada "
				+ "WHERE reemplaza_familia_id IS NOT NULL")).containsEntry("familia_id", otra)
				.containsEntry("reemplaza_familia_id", familia).containsEntry("motivo", "REEMPLAZO")
				.containsEntry("creado_por", "sistema.panel");
		List<LlamadasSemana.Familia> semana = llamadas.deEstaSemana().familias();
		assertThat(semana).filteredOn(fam -> !fam.reemplazada()).extracting(LlamadasSemana.Familia::familiaId)
				.containsExactly(otra);
		assertThat(semana).filteredOn(LlamadasSemana.Familia::reemplazada).extracting(LlamadasSemana.Familia::familiaId)
				.containsExactly(familia);
		assertThatThrownBy(() -> llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.CONFIRMA, null)))
				.as("la familia reemplazada ya no se llama").isInstanceOf(RecursoNoEncontradoException.class);

		// La pasada de cada 15 minutos no la reemplaza otra vez.
		SecurityContextHolder.clearContext();
		reemplazos.reintentar();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM muestra_llamada WHERE reemplaza_familia_id IS NOT NULL",
				Long.class)).isEqualTo(1);
	}

	@Test
	void laPasadaDeCadaQuinceMinutosReemplazaSiElReemplazoTrasElCommitNoOcurrio() {
		Long familia = familiaDeLaMuestra();
		// Dos «No contesta» que quedaron sin reemplazo (por ejemplo, el proceso después del commit falló).
		LocalDateTime primera = LUNES.plusDays(1).atTime(10, 0);
		for (int intento = 1; intento <= 2; intento++) {
			LocalDateTime cuando = primera.plusHours(intento - 1L);
			jdbc.update("INSERT INTO llamada_control (colegio_id, semana, familia_id, resultado, intento, creado_en, "
					+ "creado_por, actualizado_en) VALUES (1, ?, ?, 'NO_CONTESTA', ?, ?, 'promotora', ?)", LUNES, familia,
					intento, cuando, cuando);
		}
		a(LUNES.plusDays(1), 11, 15);

		reemplazos.reintentar();
		reemplazos.reintentar();

		assertThat(jdbc.queryForList("SELECT CONCAT(reemplaza_familia_id, ' ', creado_por) FROM muestra_llamada "
				+ "WHERE reemplaza_familia_id IS NOT NULL", String.class)).containsExactly(familia + " sistema.panel");
	}

	@Test
	void unNoContestaSeguidoDeConfirmaNoReemplazaAlaFamilia() {
		Long familia = familiaDeLaMuestra();
		UsuariosDePrueba.iniciarSesion(promotora);
		a(LUNES.plusDays(1), 10, 0);
		llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));
		a(LUNES.plusDays(1), 16, 0);
		llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.CONFIRMA, null));
		SecurityContextHolder.clearContext();

		reemplazos.reintentar();

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM muestra_llamada WHERE reemplaza_familia_id IS NOT NULL",
				Long.class)).isZero();
	}

	// ------------------------------------------------------------------ apoyo

	private Long familiaDeLaMuestra() {
		a(LUNES, 0, 10);
		muestraSemanal.ejecutar();
		return jdbc.queryForObject("SELECT familia_id FROM muestra_llamada WHERE semana = ?", Long.class, LUNES);
	}

	private List<String> claves() {
		return jdbc.queryForList("SELECT clave FROM configuracion_bd WHERE clave LIKE 'qa_%' ORDER BY clave", String.class);
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj);
	}

	private void a(LocalDate dia, int hora, int minuto) {
		reloj.fijar(dia.atTime(hora, minuto).atZone(reloj.getZone()).toInstant());
	}
}
