package pe.edu.virgenmaria.cuentasclaras.conectores;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
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
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * QA del sprint 8 (sección 3.6, H9, decisiones 118, 119 y 124; E12, E15 y E16): conectores por colegio y la serie de
 * comprobantes entre sedes con el mismo RUC.
 *
 * <p>Escenarios (Dado / Cuando / Entonces):
 * <ul>
 *   <li>Dada una sede A que ya emite con la serie B002, cuando la sede B (mismo RUC) aprueba un OSE con la serie B002,
 *       entonces la aprobación se rechaza con un mensaje claro, no en el primer cobro de su cajera (QA-S8-6).</li>
 *   <li>Dado un colegio con otro RUC, cuando aprueba la serie B002, entonces se acepta (las series son por RUC).</li>
 *   <li>Dado un cambio de cuenta, cuando pasan 7 días, entonces la alerta CRÍTICA sigue hasta el séptimo día y luego
 *       desaparece.</li>
 *   <li>Dado un conector del colegio A, cuando la Promotoría de B lo intenta aprobar con el id de su solicitud, entonces no
 *       lo encuentra.</li>
 * </ul>
 */
@PruebaIntegracion
class ConectoresQaSprint8Test {

	private static final String RUC_COMPARTIDO = EscenarioPlataforma.RUC_A;

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

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		jdbc.update("UPDATE colegio SET ruc = ?, razon_social = 'Asociación Educativa Sede Central S.A.C.' WHERE id = 1",
				RUC_COMPARTIDO);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** La sede A (colegio 1) ya emitió con esa serie de boletas (simulada). */
	private void sedeAEmitioCon(String serie) {
		LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Lima"));
		jdbc.update("INSERT INTO serie_comprobante (colegio_id, tipo, serie, proveedor, ultimo_numero, creado_en, creado_por, "
				+ "actualizado_en) VALUES (1, 'BOLETA', ?, 'SIMULADO', 1, ?, 'caja', ?)", serie, ahora, ahora);
	}

	private long sede(String nombre, String ruc) {
		return colegios.saveAndFlush(Colegio.enPreparacion(nombre, nombre + " S.A.C.", ruc, "Av. Sede 2, Lima",
				"info@" + nombre.toLowerCase().replace(' ', '-') + ".edu.pe", EnumSet.of(Nivel.PRIMARIA), "Titular", "45678931",
				null)).getId();
	}

	private static PropuestaConectorRequest oseSimulado(String boleta, String factura, String notaBoleta,
			String notaFactura) {
		return new PropuestaConectorRequest(ProveedorConector.SIMULADO, ModoConector.SIMULADO, null, boleta, factura,
				notaBoleta, notaFactura, "INAFECTO", null, null, null, null, null, null, null, null, null, null,
				"Empezamos con el comprobante simulado en esta sede");
	}

	/**
	 * Administración propone y prueba; Dirección (otra persona) aprueba con su firma. El colegio tiene a quién avisar: una
	 * Promotoría con celular y el correo externo de control.
	 */
	private Long proponerProbarYAprobar(long colegio, String sufijo, PropuestaConectorRequest pedido) {
		UsuariosDePrueba.guardar(usuarios, codificador, colegio, "promotoria." + sufijo, UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		if (jdbc.queryForObject("SELECT COUNT(*) FROM configuracion_colegio WHERE colegio_id = ?", Long.class, colegio) == 0) {
			jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en, creado_por) VALUES (?, "
					+ "'huella_correo_externo', 'contador@estudio.pe', NOW(), 'operador.pruebas')", colegio);
		}
		Usuario administracion = UsuariosDePrueba.guardar(usuarios, codificador, colegio, "administracion." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.ADMINISTRACION);
		Usuario direccion = UsuariosDePrueba.guardar(usuarios, codificador, colegio, "direccion." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		UsuariosDePrueba.iniciarSesion(administracion);
		Long id = propuesta.proponer(TipoConector.OSE, pedido);
		propuesta.probar(id);
		SecurityContextHolder.clearContext();
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM conector_colegio WHERE id = ?", Long.class, id);
		UsuariosDePrueba.iniciarSesion(direccion);
		try {
			bandeja.aprobar(solicitud, null);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		return id;
	}

	private String estado(Long id) {
		return jdbc.queryForObject("SELECT estado FROM conector_colegio WHERE id = ?", String.class, id);
	}

	@Test
	@Disabled("QA-S8-6: la aprobación del OSE de una sede no revisa las series que ya usa otra sede con el mismo RUC; "
			+ "solo trg_serie_comprobante_nace (MySQL) lo detecta al abrir la serie, en el primer cobro de la cajera")
	void debeRechazarAprobarUnOseConUnaSerieQueYaUsaOtraSedeDelMismoRuc() {
		sedeAEmitioCon("B002");
		long sedeB = sede("Colegio Sede Norte", RUC_COMPARTIDO);

		assertThatThrownBy(() -> proponerProbarYAprobar(sedeB, "norte", oseSimulado("B002", "F012", "BC12", "FC12")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("B002");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conector_colegio WHERE colegio_id = ? AND estado = 'VIGENTE'",
				Long.class, sedeB)).isZero();
	}

	@Test
	void debeAceptarLaMismaSerieEnUnColegioConOtroRuc() {
		sedeAEmitioCon("B002");
		long otro = sede("Colegio Independiente", EscenarioPlataforma.RUC_B);

		Long id = proponerProbarYAprobar(otro, "independiente", oseSimulado("B002", "F012", "BC12", "FC12"));

		assertThat(estado(id)).isEqualTo("VIGENTE");
	}

	@Test
	void debeUsarCadaColegioSuPropioOseSinVerElDeOtraSede() {
		long sedeB = sede("Colegio Sede Sur", RUC_COMPARTIDO);
		Long id = proponerProbarYAprobar(sedeB, "sur", oseSimulado("B022", "F022", "BC22", "FC22"));

		assertThat(estado(id)).isEqualTo("VIGENTE");
		assertThat(ContextoColegio.en(sedeB, () -> conectores.vigente(TipoConector.OSE)))
				.hasValueSatisfying(c -> assertThat(c.id()).isEqualTo(id));
		assertThat(ContextoColegio.en(1L, () -> conectores.vigente(TipoConector.OSE))).isEmpty();
	}

	@Test
	void debeImpedirQueOtroColegioApruebeElCambioDeCuentaDeEsteColegio() throws Exception {
		Usuario promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.cuenta8", UsuariosDePrueba.CLAVE,
				false, Rol.PROMOTOR);
		UsuariosDePrueba.iniciarSesion(promotora);
		Long id = propuesta.proponer(TipoConector.CUENTA_BANCARIA, CambioConectorTest.cuenta(ProveedorConector.BCP,
				"1912345678901"));
		propuesta.probar(id);
		SecurityContextHolder.clearContext();
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM conector_colegio WHERE id = ?", Long.class, id);
		long otro = sede("Colegio Ajeno", EscenarioPlataforma.RUC_C);
		Usuario promotoraOtro = UsuariosDePrueba.guardar(usuarios, codificador, otro, "promotora.ajena8",
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);

		int respuesta = mvc.perform(post("/aprobaciones/" + solicitud + "/aprobar").with(csrf())
				.with(UsuariosDePrueba.como(promotoraOtro))).andReturn().getResponse().getStatus();

		assertThat(respuesta).as("la solicitud de otro colegio no existe para esta Promotoría").isIn(302, 403, 404);
		assertThat(estado(id)).isEqualTo("PROPUESTO");
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("PENDIENTE");
	}

	// ------------------------------------------------------------------ alerta CRÍTICA de 7 días

	private void cuentaVigenteDesde(LocalDateTime cuando) {
		jdbc.update("INSERT INTO conector_colegio (colegio_id, tipo, proveedor, modo, configuracion, clave_publica, estado, "
				+ "vigente, propuesto_por, resuelto_por, resuelto_en, prueba_ok, creado_en, creado_por, actualizado_en) VALUES "
				+ "(1, 'CUENTA_BANCARIA', 'BCP', 'REAL', ?, ?, 'VIGENTE', TRUE, 'a.prueba', 'b.prueba', ?, TRUE, ?, 'a.prueba', ?)",
				"{\"numero\":\"1912345678901\",\"cci\":\"00219100123456789012\",\"moneda\":\"PEN\"}",
				UUID.randomUUID().toString(), cuando, cuando, cuando);
	}

	@Test
	void debeMostrarLaAlertaCriticaDelCambioDeCuentaHastaElSeptimoDia() {
		cuentaVigenteDesde(LocalDateTime.now(ZoneId.of("America/Lima")).minusDays(7).plusMinutes(5));
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));

		assertThat(alertas.alertas()).hasSize(1).allSatisfy(a -> assertThat(a.texto()).contains("a.prueba", "b.prueba")
				.doesNotContain("1912345678901"));
	}

	@Test
	void debeQuitarLaAlertaCriticaDelCambioDeCuentaPasadoElSeptimoDia() {
		cuentaVigenteDesde(LocalDateTime.now(ZoneId.of("America/Lima")).minusDays(7).minusMinutes(5));
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.PROMOTOR));

		assertThat(alertas.alertas()).isEmpty();
	}
}
