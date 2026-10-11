package pe.edu.virgenmaria.cuentasclaras.conectores;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConectorVigente;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ModoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.AlertasConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.ConectoresColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.PropuestaConector;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 8, tanda 2 (sección 3.6, E12 y E13): cambiar un conector es cambiar adónde entra el dinero. Lo propone
 * Administración o Promotoría, se prueba la conexión y lo aprueba OTRA persona de Promotoría o Dirección con su firma; si
 * mueve dinero, una Promotoría participa. Al entrar en vigencia, el anterior queda REEMPLAZADO, toda la Promotoría y el
 * correo externo de control reciben el aviso y «Para revisar» muestra una alerta CRÍTICA. El secreto nunca vuelve a la
 * pantalla ni a la bitácora; y el colegio B no ve ni prueba los conectores del A.
 */
@PruebaIntegracion
class CambioConectorTest {

	private static final String TOKEN = "nubefact-token-secreto-0123456789-QWER";

	@Autowired
	private PropuestaConector propuesta;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private ConectoresColegio conectores;

	@Autowired
	private AlertasConectores alertas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private MockMvc mvc;

	private Usuario promotora;

	private Usuario promotora2;

	private Usuario directora;

	private Usuario administracion;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		jdbc.update("UPDATE colegio SET ruc = '20600000013', razon_social = 'Colegio Uno S.A.C.' WHERE id = 1");
		jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en, creado_por) VALUES (1, "
				+ "'huella_correo_externo', 'contador@estudio.pe', NOW(), 'operador.pruebas')");
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.con", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		promotora2 = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora2.con", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora.con", UsuariosDePrueba.CLAVE, false,
				Rol.DIRECTOR);
		administracion = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "administracion.con", UsuariosDePrueba.CLAVE,
				false, Rol.ADMINISTRACION);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	static PropuestaConectorRequest cuenta(ProveedorConector banco, String numero) {
		return new PropuestaConectorRequest(banco, ModoConector.REAL, null, null, null, null, null, null, null, null, null,
				numero, "00219100" + numero.substring(numero.length() - 12), null, null, null, null, null,
				"Cuenta del colegio para depositar el efectivo");
	}

	static PropuestaConectorRequest oseNubefact(String token) {
		return new PropuestaConectorRequest(ProveedorConector.NUBEFACT, ModoConector.REAL,
				"https://api.nubefact.com/api/v1/abc-123", "B002", "F002", "BC02", "FC02", "INAFECTO", null, null, null, null,
				null, null, null, null, token, null, "Cuenta de Nubefact del colegio, ya habilitada");
	}

	private Long proponer(Usuario quien, TipoConector tipo, PropuestaConectorRequest pedido) {
		UsuariosDePrueba.iniciarSesion(quien);
		Long id = propuesta.proponer(tipo, pedido);
		SecurityContextHolder.clearContext();
		return id;
	}

	private void probar(Usuario quien, Long id) {
		UsuariosDePrueba.iniciarSesion(quien);
		propuesta.probar(id);
		SecurityContextHolder.clearContext();
	}

	private Long solicitudDe(Long conectorId) {
		return jdbc.queryForObject("SELECT solicitud_id FROM conector_colegio WHERE id = ?", Long.class, conectorId);
	}

	private void aprobar(Usuario quien, Long conectorId) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			bandeja.aprobar(solicitudDe(conectorId), null);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private String estado(Long id) {
		return jdbc.queryForObject("SELECT estado FROM conector_colegio WHERE id = ?", String.class, id);
	}

	@Test
	void quienProponeNoAprueba() {
		Long id = proponer(promotora, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.BCP, "1912345678901"));
		probar(promotora, id);

		assertThatThrownBy(() -> aprobar(promotora, id)).isInstanceOf(AutoaprobacionSolicitudException.class);
		assertThat(estado(id)).isEqualTo("PROPUESTO");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'AUTOAPROBACION_RECHAZADA'",
				Long.class)).isEqualTo(1L);
	}

	@Test
	void sinPruebaDeConexionNoEntraEnVigencia() {
		Long id = proponer(promotora, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.BCP, "1912345678901"));

		assertThatThrownBy(() -> aprobar(directora, id)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("probar la conexión");
		assertThat(estado(id)).isEqualTo("PROPUESTO");
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitudDe(id)))
				.isEqualTo("PENDIENTE");
	}

	@Test
	void sinPromotoriaNoEntraEnVigencia() {
		// Administración propone la cuenta bancaria y Dirección la aprueba: no hay Promotoría de por medio.
		Long id = proponer(administracion, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.BBVA, "0011123456789"));
		probar(administracion, id);

		assertThatThrownBy(() -> aprobar(directora, id)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Promotoría");
		assertThat(estado(id)).isEqualTo("PROPUESTO");

		// Con Promotoría como aprobadora, sí.
		aprobar(promotora, id);
		assertThat(estado(id)).isEqualTo("VIGENTE");
	}

	@Test
	void elOsePuedeAprobarloDireccion() {
		Long id = proponer(administracion, TipoConector.OSE, new PropuestaConectorRequest(ProveedorConector.SIMULADO,
				ModoConector.SIMULADO, null, "B003", "F003", "BC03", "FC03", "EXONERADO", null, null, null, null, null, null,
				null, null, null, null, "Seguimos con el comprobante simulado"));
		probar(administracion, id);
		aprobar(directora, id);

		assertThat(estado(id)).isEqualTo("VIGENTE");
	}

	@Test
	void avisaATodaLaPromotoriaYAlCorreoExternoYReemplazaAlVigente() {
		Long primera = proponer(promotora, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.BCP, "1912345678901"));
		probar(promotora, primera);
		aprobar(directora, primera);
		assertThat(estado(primera)).isEqualTo("VIGENTE");

		// Cambiar la cuenta: la propone Administración y la aprueba otra Promotoría.
		Long segunda = proponer(administracion, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.INTERBANK,
				"2003004005006"));
		probar(administracion, segunda);
		aprobar(promotora2, segunda);

		assertThat(estado(primera)).isEqualTo("REEMPLAZADO");
		assertThat(estado(segunda)).isEqualTo("VIGENTE");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conector_colegio WHERE tipo = 'CUENTA_BANCARIA' AND vigente",
				Long.class)).isEqualTo(1L);
		// Mensajes del segundo cambio: a las dos Promotorías (las cuentas del personal) y al correo externo.
		List<String> destinos = jdbc.queryForList("SELECT destinatario_tipo || ':' || COALESCE(CAST(usuario_id AS VARCHAR), "
				+ "destino) FROM mensaje WHERE tipo = 'CONECTOR_VIGENTE' AND entidad_id = ?", String.class, segunda);
		assertThat(destinos).containsExactlyInAnyOrder("USUARIO:" + promotora.getId(), "USUARIO:" + promotora2.getId(),
				"EXTERNO:contador@estudio.pe");
		String texto = jdbc.queryForObject("SELECT parametros FROM mensaje WHERE tipo = 'CONECTOR_VIGENTE' AND entidad_id = ? "
				+ "AND destinatario_tipo = 'EXTERNO'", String.class, segunda);
		assertThat(texto).contains("cuenta que termina en 5006", "administracion.con", "promotora2.con")
				.doesNotContain("2003004005006");
		// Bitácora resaltada, sin el número completo.
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CONECTOR_VIGENTE")).containsEntry("entidad_id", segunda.toString());
		// Alerta CRÍTICA en «Para revisar» (las dos entradas en vigencia de esta semana).
		UsuariosDePrueba.iniciarSesion(promotora);
		List<AlertaRevision> criticas = alertas.alertas();
		assertThat(criticas).hasSize(2).allMatch(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA)
				.allMatch(AlertaRevision::difundible);
		assertThat(criticas.getFirst().texto()).contains("administracion.con", "promotora2.con");
		// Y la caja deposita en la cuenta vigente.
		assertThat(ContextoColegio.en(1L, () -> conectores.vigente(TipoConector.CUENTA_BANCARIA)).map(ConectorVigente::id))
				.contains(segunda);
	}

	@Test
	void unRechazoLlevaLaFirmaYDejaElConectorAnterior() {
		Long id = proponer(promotora, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.BCP, "1912345678901"));
		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.rechazar(solicitudDe(id), "El número no coincide con el estado de cuenta del banco");
		SecurityContextHolder.clearContext();

		assertThat(estado(id)).isEqualTo("RECHAZADO");
		assertThat(jdbc.queryForObject("SELECT resuelto_por FROM conector_colegio WHERE id = ?", String.class, id))
				.isEqualTo("directora.con");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", Long.class,
				"conector_colegio:" + id + ":RECHAZADO")).isEqualTo(1L);
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CONECTOR_RECHAZADO")).isNotNull();
	}

	@Test
	void unTipoTieneUnSoloCambioPropuestoALaVez() {
		proponer(promotora, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.BCP, "1912345678901"));

		assertThatThrownBy(() -> proponer(administracion, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.BBVA,
				"0011123456789"))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("esperando aprobación");
	}

	@Test
	void elSecretoNoVuelveALaPantallaNiALaBitacora() throws Exception {
		Long id = proponer(administracion, TipoConector.OSE, oseNubefact(TOKEN));

		assertThat(jdbc.queryForObject("SELECT secreto_pista FROM conector_colegio WHERE id = ?", String.class, id))
				.isEqualTo("QWER");
		byte[] cifrado = jdbc.queryForObject("SELECT secreto_cifrado FROM conector_colegio WHERE id = ?", byte[].class, id);
		assertThat(new String(cifrado, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(TOKEN);
		assertThat(EscenarioEscolar.todaLaBitacora(jdbc)).doesNotContain(TOKEN).contains("QWER");
		assertThat(jdbc.queryForObject("SELECT datos || resumen FROM solicitud_cambio WHERE id = ?", String.class,
				solicitudDe(id))).doesNotContain(TOKEN);
		mvc.perform(get("/conectores").with(UsuariosDePrueba.como(promotora))).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("QWER")))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(TOKEN))));
		mvc.perform(get("/aprobaciones").with(UsuariosDePrueba.como(directora))).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(TOKEN))));
		// Descifrado solo dentro de conectores, para el colegio dueño: el conector de este colegio trae su token.
		assertThat(jdbc.queryForObject("SELECT clave_version FROM conector_colegio WHERE id = ?", Integer.class, id))
				.isEqualTo(1);
	}

	@Test
	void soloPromotoriaOAdministracionProponenYCajaNoVeLosConectores() throws Exception {
		Usuario caja = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.con", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		assertThatThrownBy(() -> proponer(directora, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.BCP,
				"1912345678901"))).isInstanceOf(AccessDeniedException.class);
		mvc.perform(get("/conectores").with(UsuariosDePrueba.como(caja))).andExpect(status().isForbidden());
		mvc.perform(get("/conectores").with(UsuariosDePrueba.como(directora))).andExpect(status().isOk());
		mvc.perform(post("/conectores/cuenta-bancaria").with(csrf()).with(UsuariosDePrueba.como(promotora))
				.param("proveedor", "BCP").param("modo", "REAL").param("numeroCuenta", "191-2345678-9-01")
				.param("cci", "00219100234567890112").param("motivo", "Cuenta corriente del colegio en soles"))
				.andExpect(redirectedUrl("/conectores"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conector_colegio WHERE estado = 'PROPUESTO'", Long.class))
				.isEqualTo(1L);
	}

	@Test
	void elColegioBNoVeNiPruebaLosConectoresDelA() throws Exception {
		Long id = proponer(promotora, TipoConector.CUENTA_BANCARIA, cuenta(ProveedorConector.BCP, "1912345678901"));
		long colegioB = colegios.saveAndFlush(Colegio.enPreparacion("Colegio B", "Colegio B S.A.C.", EscenarioPlataforma.RUC_B,
				"Av. 2", "info@b.edu.pe", EnumSet.of(Nivel.PRIMARIA), "Titular B", "45678921", null)).getId();
		Usuario promotoraB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "promotora.b.con",
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);

		mvc.perform(post("/conectores/" + id + "/probar").with(csrf()).with(UsuariosDePrueba.como(promotoraB)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/conectores").with(UsuariosDePrueba.como(promotoraB))).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("8901"))));
		assertThat(ContextoColegio.en(colegioB, () -> conectores.vigente(TipoConector.CUENTA_BANCARIA))).isEmpty();
		assertThat(jdbc.queryForObject("SELECT prueba_ok FROM conector_colegio WHERE id = ?", Boolean.class, id)).isNull();
	}

	@Test
	void lasAlertasDesaparecenPasadosLosDiasDelAviso() {
		EscenarioPuestaEnMarchaConectores.vigenteHaceDias(jdbc, 1L, 8);
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThat(alertas.alertas()).isEmpty();
		EscenarioPuestaEnMarchaConectores.vigenteHaceDias(jdbc, 1L, 2);
		assertThat(alertas.alertas()).hasSize(1);
	}

	/** Conectores insertados directo (H2) con su fecha de vigencia. */
	static final class EscenarioPuestaEnMarchaConectores {

		static void vigenteHaceDias(JdbcTemplate jdbc, long colegio, int dias) {
			LocalDateTime cuando = LocalDateTime.now(java.time.ZoneId.of("America/Lima")).minusDays(dias);
			jdbc.update("UPDATE conector_colegio SET estado = 'REEMPLAZADO', vigente = NULL WHERE colegio_id = ? AND tipo = "
					+ "'RECAUDACION' AND estado = 'VIGENTE'", colegio);
			jdbc.update("INSERT INTO conector_colegio (colegio_id, tipo, proveedor, modo, configuracion, clave_publica, estado, "
					+ "vigente, propuesto_por, resuelto_por, resuelto_en, prueba_ok, creado_en, creado_por, actualizado_en) VALUES "
					+ "(?, 'RECAUDACION', 'GENERICO', 'REAL', ?, ?, 'VIGENTE', TRUE, 'a.prueba', 'b.prueba', ?, TRUE, ?, "
					+ "'a.prueba', ?)", colegio, "{\"modalidad\":\"SIN_BASE\",\"patronAbono\":\"\",\"abono\":\"POR_LOTE\"}",
					java.util.UUID.randomUUID().toString(), cuando, cuando, cuando);
		}
	}
}
