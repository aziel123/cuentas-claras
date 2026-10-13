package pe.edu.virgenmaria.cuentasclaras.operacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.ResolucionRespaldoRepository;
import pe.edu.virgenmaria.cuentasclaras.operacion.repository.RespaldoRepository;
import pe.edu.virgenmaria.cuentasclaras.operacion.service.ResolucionesRespaldo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Correcciones del sprint 7 (QA-S7-1): la alerta «Faltan filas» de un respaldo sigue hasta que una PERSONA de Promotoría
 * la resuelve con motivo (y su firma); queda en la bitácora; no la resuelve Dirección, ni el operador de los respaldos,
 * ni sin motivo. Reloj: viernes 2 de octubre de 2026, 08:00 en Lima.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ResolucionRespaldoTest {

	private static final String MOTIVO = "Revisado con el responsable técnico: purga de accesos de hace 2 años";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private ResolucionesRespaldo resoluciones;

	@Autowired
	private RespaldoRepository respaldos;

	@Autowired
	private ResolucionRespaldoRepository resolucionesRepo;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private FirmaSesion firma;

	@Autowired
	private AuditoriaService auditoria;

	private Usuario promotora;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.respaldos", UsuariosDePrueba.CLAVE,
				false, Rol.PROMOTOR);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void promotoriaResuelveConMotivoLaAlertaSigueHastaEntoncesYQuedaEnLaBitacora() throws Exception {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS", "pago (faltan 1)");
		respaldo("cc-20261002-024500.sql.gz.age", "2026-10-02T02:46:00", "IGUAL", null);
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("REVISAR"));

		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(resoluciones.resolver(MOTIVO)).isEqualTo("cc-20261002-023000.sql.gz.age");
		SecurityContextHolder.clearContext();

		mvc.perform(get("/salud/respaldo")).andExpect(content().string("OK"));
		assertThat(jdbc.queryForMap("SELECT motivo, creado_por, colegio_id FROM resolucion_respaldo"))
				.containsEntry("motivo", MOTIVO).containsEntry("creado_por", "promotora.respaldos")
				.containsEntry("colegio_id", 1L);
		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = 'RESPALDO_FALTAN_FILAS_RESUELTO'",
				String.class)).contains("pago (faltan 1)").contains(MOTIVO);
	}

	@Test
	void unaFaltaNuevaDespuesDeResolverVuelveAAvisar() throws Exception {
		respaldo("cc-20261001-023000.sql.gz.age", "2026-10-01T02:31:10", "FALTAN_FILAS", "pago (faltan 1)");
		UsuariosDePrueba.iniciarSesion(promotora);
		resoluciones.resolver(MOTIVO);
		SecurityContextHolder.clearContext();
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("ATRASADO"));

		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS", "mensaje (faltan 2)");

		mvc.perform(get("/salud/respaldo")).andExpect(content().string("REVISAR"));
	}

	@Test
	void sinMotivoNoSeResuelve() {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS", "pago (faltan 1)");
		UsuariosDePrueba.iniciarSesion(promotora);

		assertThatThrownBy(() -> resoluciones.resolver("ok")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("motivo");

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resolucion_respaldo", Long.class)).isZero();
	}

	@Test
	void direccionNoLaResuelve() {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS", "pago (faltan 1)");
		Usuario directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora.respaldos",
				UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		UsuariosDePrueba.iniciarSesion(directora);

		assertThatThrownBy(() -> resoluciones.resolver(MOTIVO)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void elOperadorDeLosRespaldosNoResuelveSuPropiaAlerta() {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS", "pago (faltan 1)");
		jdbc.update("UPDATE usuario SET correo = 'operador@colegio.pe' WHERE id = ?", promotora.getId());
		PropiedadesMonitoreo conOperador = new PropiedadesMonitoreo("Operador@Colegio.pe", true, true, 26, 5,
				Duration.ofMinutes(15), 0, Duration.ofMinutes(10), Duration.ofHours(1));
		ResolucionesRespaldo servicio = new ResolucionesRespaldo(respaldos, resolucionesRepo, usuarios, firma, auditoria,
				conOperador, reloj);
		UsuariosDePrueba.iniciarSesion(promotora);

		assertThatThrownBy(() -> servicio.resolver(MOTIVO)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("operador");
	}

	@Test
	void sinAlertaNoHayNadaQueResolver() {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "IGUAL", null);
		UsuariosDePrueba.iniciarSesion(promotora);

		assertThatThrownBy(() -> resoluciones.resolver(MOTIVO)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No hay ninguna alerta");
	}

	@Test
	void porLaWebPromotoriaLaResuelveYSeVeElAviso() throws Exception {
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS", "pago (faltan 1)");
		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(promotora)))
				.andExpect(content().string(containsString("Resolver la alerta")));

		mvc.perform(post("/panel/sistema/respaldo/resolver").param("motivo", MOTIVO).with(csrf())
				.with(UsuariosDePrueba.como(promotora))).andExpect(redirectedUrl("/panel/sistema"));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resolucion_respaldo", Long.class)).isEqualTo(1L);
	}

	// ------------------------------------------------------------------ sprint 8, tanda 3: por colegio (V30, E25)

	@Test
	void laFaltaDeOtroColegioNoLaVeNiLaResuelveEstaPromotoria() throws Exception {
		long colegioB = colegioB();
		Usuario promotoraB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "promotora.b.respaldos",
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS",
				"pago (faltan 1) [colegio " + colegioB + "]", "[" + colegioB + "]");
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("REVISAR"));

		// El colegio 1 no tiene nada que resolver ni ve la falta del B en su panel.
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThatThrownBy(() -> resoluciones.resolver(MOTIVO)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No hay ninguna alerta");
		SecurityContextHolder.clearContext();
		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(promotora)))
				.andExpect(content().string(not(containsString("Resolver la alerta"))))
				.andExpect(content().string(not(containsString("pago (faltan 1)"))));

		// La Promotoría del B sí: con su firma (respaldo:{id}:{colegio}:RESUELTO) y en la bitácora de SU colegio.
		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(promotoraB)))
				.andExpect(content().string(containsString("Resolver la alerta")))
				.andExpect(content().string(containsString("pago (faltan 1)")));
		UsuariosDePrueba.iniciarSesion(promotoraB);
		assertThat(resoluciones.resolver(MOTIVO)).isEqualTo("cc-20261002-023000.sql.gz.age");
		SecurityContextHolder.clearContext();
		Long respaldoId = jdbc.queryForObject("SELECT id FROM respaldo", Long.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ? AND colegio_id = ?",
				Long.class, "respaldo:" + respaldoId + ":" + colegioB + ":RESUELTO", colegioB)).isEqualTo(1L);
		assertThat(jdbc.queryForObject("SELECT colegio_id FROM resolucion_respaldo", Long.class)).isEqualTo(colegioB);
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("OK"));
	}

	@Test
	void conDosColegiosAfectadosLaAlertaSigueHastaQueResuelvanLosDos() throws Exception {
		long colegioB = colegioB();
		Usuario promotoraB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "promotora.b.dos",
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS",
				"ancla de la bitácora (evento 9), pago (faltan 2) [colegio " + colegioB + "]", "[1," + colegioB + "]");

		UsuariosDePrueba.iniciarSesion(promotora);
		resoluciones.resolver(MOTIVO);
		SecurityContextHolder.clearContext();
		// El colegio 1 ya resolvió la suya; la instalación sigue con una falta abierta (la del B).
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("REVISAR"));
		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(promotora)))
				.andExpect(content().string(not(containsString("Resolver la alerta"))));
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThatThrownBy(() -> resoluciones.resolver(MOTIVO)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya se resolvió");
		SecurityContextHolder.clearContext();
		// La bitácora del colegio 1 no dice qué le faltó al B (solo que hubo faltas en otro colegio).
		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = 'RESPALDO_FALTAN_FILAS_RESUELTO'",
				String.class)).contains("ancla de la bitácora").doesNotContain("pago (faltan 2)")
				.contains("otros colegios");

		UsuariosDePrueba.iniciarSesion(promotoraB);
		resoluciones.resolver(MOTIVO);
		SecurityContextHolder.clearContext();
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("OK"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resolucion_respaldo", Long.class)).isEqualTo(2L);
	}

	@Test
	void unaResolucionPosteriorDeOtroColegioNoCierraLaFaltaDeEste() throws Exception {
		long colegioB = colegioB();
		Usuario promotoraB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "promotora.b.posterior",
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		respaldo("cc-20261001-023000.sql.gz.age", "2026-10-01T02:31:10", "FALTAN_FILAS", "pago (faltan 1) [colegio 1]",
				"[1]");
		respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "FALTAN_FILAS",
				"alerta sin resolver desde el respaldo cc-20261001-023000.sql.gz.age [colegio 1], mensaje (faltan 1) "
						+ "[colegio " + colegioB + "]", "[1," + colegioB + "]");
		UsuariosDePrueba.iniciarSesion(promotoraB);
		resoluciones.resolver(MOTIVO);
		SecurityContextHolder.clearContext();

		// El B resolvió el respaldo 2, pero la falta del colegio 1 (respaldos 1 y 2) sigue abierta.
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("REVISAR"));
		mvc.perform(get("/panel/sistema").with(UsuariosDePrueba.como(promotora)))
				.andExpect(content().string(containsString("Resolver la alerta")));
		// Promotoría del colegio 1 resuelve el ÚLTIMO que lo nombra y eso cierra también el primero.
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(resoluciones.resolver(MOTIVO)).isEqualTo("cc-20261002-023000.sql.gz.age");
		SecurityContextHolder.clearContext();
		mvc.perform(get("/salud/respaldo")).andExpect(content().string("OK"));
	}

	@Test
	void colegiosFaltanSoloConFaltanFilasYConFormatoDeArreglo() {
		assertThatThrownBy(() -> respaldo("cc-20261002-023000.sql.gz.age", "2026-10-02T02:31:10", "IGUAL", null, "[1]"))
				.as("IGUAL con colegios").isInstanceOf(DataIntegrityViolationException.class);
		for (String malo : new String[] { "1", "[1,]", "[]", "[1, 2]", "[a]", "{1}" }) {
			assertThatThrownBy(() -> respaldo("cc-20261002-024500.sql.gz.age", "2026-10-02T02:46:10", "FALTAN_FILAS",
					"pago (faltan 1)", malo)).as(malo).isInstanceOf(DataIntegrityViolationException.class);
		}
		respaldo("cc-20261002-025000.sql.gz.age", "2026-10-02T02:51:10", "FALTAN_FILAS", "pago (faltan 1)", "[1,25]");
	}

	// ------------------------------------------------------------------ apoyo

	private long colegioB() {
		jdbc.update("INSERT INTO colegio (nombre) VALUES ('Colegio B de respaldos')");
		return jdbc.queryForObject("SELECT MAX(id) FROM colegio WHERE nombre = 'Colegio B de respaldos'", Long.class);
	}

	private void respaldo(String archivo, String fin, String comparacion, String diferencias) {
		respaldo(archivo, fin, comparacion, diferencias, null);
	}

	private void respaldo(String archivo, String fin, String comparacion, String diferencias, String colegiosFaltan) {
		LocalDateTime termino = LocalDateTime.parse(fin);
		jdbc.update("INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, secuencia_antes, "
				+ "hash_antes, secuencia_despues, hash_despues, conteos, destino, comparacion, diferencias, colegios_faltan, "
				+ "creado_en, creado_por) VALUES (?, ?, ?, REPEAT('a', 64), 1024, '30', 0, REPEAT('0', 64), 0, "
				+ "REPEAT('0', 64), '{\"pago\":[1,1]}', 'simulado', ?, ?, ?, ?, 'cc_respaldo')", termino.minusMinutes(1),
				termino, archivo, comparacion, diferencias, colegiosFaltan, termino);
	}
}
