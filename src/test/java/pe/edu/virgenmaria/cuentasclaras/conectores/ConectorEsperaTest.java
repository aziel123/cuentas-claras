package pe.edu.virgenmaria.cuentasclaras.conectores;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.Equipo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.PropuestaConectorRequest;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ProveedorConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.proceso.AplicacionConectoresEnEspera;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.EsperaConectores;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.PropuestaConector;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.FrenosOperador;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.CORREO_CONTROL;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (decisión 218 y A1c-4): la cuenta bancaria (o la pasarela) que la Promotoría propone y aprueba con
 * su código queda EN_ESPERA 24 horas: entra sola al vencer ({@code sistema.conectores}), salvo que la frene otra
 * Promotoría con 30 días en el rol o un operador; la socia la puede aprobar al instante con SU código. El OSE (no mueve
 * dinero) entra al instante con el código.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ConectorEsperaTest {

	@Autowired
	private PropuestaConector propuesta;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private EsperaConectores espera;

	@Autowired
	private AplicacionConectoresEnEspera aplicacion;

	@Autowired
	private FrenosOperador frenos;

	@Autowired
	private DerivadorSecreto derivador;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private Equipo e;

	@BeforeEach
	void preparar() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		jdbc.update("UPDATE colegio SET ruc = '20600000013', razon_social = 'Colegio Uno S.A.C.' WHERE id = 1");
		e = EscenarioDirecta.preparar(usuarios, codificador, jdbc, reloj);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** La Promotoría propone, prueba y aprueba con su código: devuelve el conector. */
	private Long directo(TipoConector tipo, PropuestaConectorRequest propuestaRequest) {
		Long id = como(e.promotora(), () -> propuesta.proponer(tipo, propuestaRequest));
		if (tipo == TipoConector.OSE) {
			jdbc.update("UPDATE conector_colegio SET prueba_ok = TRUE, prueba_en = NOW() WHERE id = ?", id);
		}
		else {
			como(e.promotora(), () -> propuesta.probar(id));
		}
		Long solicitud = jdbc.queryForObject("SELECT solicitud_id FROM conector_colegio WHERE id = ?", Long.class, id);
		CodigoPromotoria.Pedido pedido = como(e.promotora(), () -> bandeja.pedirCodigo(solicitud));
		como(e.promotora(), () -> bandeja.aprobar(solicitud, null, false, java.util.List.of(),
				EscenarioDirecta.escrito(jdbc, derivador, pedido)));
		return id;
	}

	private Long cuentaEnEspera() {
		return directo(TipoConector.CUENTA_BANCARIA, CambioConectorTest.cuenta(ProveedorConector.BCP, "1912345678901"));
	}

	private String estado(Long id) {
		return jdbc.queryForObject("SELECT estado FROM conector_colegio WHERE id = ?", String.class, id);
	}

	@Test
	void laCuentaBancariaEsperaVeinticuatroHorasYEntraSola() {
		Long id = cuentaEnEspera();
		assertThat(estado(id)).isEqualTo("EN_ESPERA");
		assertThat(jdbc.queryForObject("SELECT directa FROM conector_colegio WHERE id = ?", Boolean.class, id)).isTrue();
		assertThat(EscenarioDirecta.destinos(jdbc, "DIRECTA_EN_ESPERA")).contains(CORREO_CONTROL);
		assertThat(EscenarioDirecta.avisados(jdbc, "DIRECTA_EN_ESPERA")).contains(e.promotora().getId(),
				e.socia().getId());

		((RelojAjustable) reloj).avanzar(Duration.ofHours(23));
		assertThat(aplicacion.aplicar(1L)).isZero();
		assertThat(estado(id)).isEqualTo("EN_ESPERA");

		((RelojAjustable) reloj).avanzar(Duration.ofHours(2));
		assertThat(aplicacion.aplicar(1L)).isEqualTo(1);
		assertThat(estado(id)).isEqualTo("VIGENTE");
		assertThat(jdbc.queryForObject("SELECT vigente FROM conector_colegio WHERE id = ?", Boolean.class, id)).isTrue();
	}

	@Test
	void laSociaLaFrenaYNoEntra() {
		Long id = cuentaEnEspera();
		// Quien la propuso no la frena (si no fue ella, cambia su clave).
		assertThatThrownBy(() -> como(e.promotora(), () -> espera.frenar(id, "No reconozco esta cuenta del banco")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Tú lo propusiste");
		como(e.socia(), () -> espera.frenar(id, "No reconozco esta cuenta del banco"));

		assertThat(jdbc.queryForMap("SELECT estado, frenado_por, motivo_freno FROM conector_colegio WHERE id = ?", id))
				.containsEntry("estado", "FRENADO").containsEntry("frenado_por", "socia.dir")
				.containsEntry("motivo_freno", "No reconozco esta cuenta del banco");
		assertThat(EscenarioDirecta.destinos(jdbc, "DIRECTA_FRENADA")).contains(CORREO_CONTROL);
		((RelojAjustable) reloj).avanzar(Duration.ofHours(25));
		assertThat(aplicacion.aplicar(1L)).isZero();
		assertThat(estado(id)).isEqualTo("FRENADO");
	}

	@Test
	void unaPromotoriaNuevaNoFrenaNiApruebaAlInstante() {
		Long id = cuentaEnEspera();
		// La «socia» tiene el rol desde hace una semana (socio de paja, A1c-2).
		EscenarioDirecta.rolDesde(jdbc, e.socia(), Rol.PROMOTOR, LocalDateTime.now(reloj).minusDays(7));
		assertThatThrownBy(() -> como(e.socia(), () -> espera.frenar(id, "No reconozco esta cuenta del banco")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("30 días");
		assertThatThrownBy(() -> como(e.socia(), () -> {
			CodigoPromotoria.Pedido pedido = espera.pedirCodigoDelSocio(id);
			espera.aprobarAhora(id, EscenarioDirecta.escrito(jdbc, derivador, pedido));
		})).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("30 días");
		assertThat(estado(id)).isEqualTo("EN_ESPERA");
	}

	@Test
	void elOperadorLaFrena() {
		Long id = cuentaEnEspera();
		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.esperas");
		UsuariosDePrueba.iniciarSesion(operador);
		try {
			assertThat(frenos.esperas()).anyMatch(x -> x.espera().id().equals(id));
			frenos.frenarConector(1L, id, "El colegio llamó: no reconoce la cuenta");
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(jdbc.queryForMap("SELECT estado, frenado_por FROM conector_colegio WHERE id = ?", id))
				.containsEntry("estado", "FRENADO").containsEntry("frenado_por", "operador.esperas");
		assertThat(EscenarioDirecta.avisados(jdbc, "DIRECTA_FRENADA")).contains(e.promotora().getId(), e.socia().getId());
	}

	@Test
	void laSociaLaApruebaAlInstanteConSuCodigoPeroNoQuienLaPropuso() {
		Long id = cuentaEnEspera();
		assertThatThrownBy(() -> como(e.promotora(), () -> espera.aprobarAhora(id, null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Tú lo propusiste");

		CodigoPromotoria.Pedido pedido = como(e.socia(), () -> espera.pedirCodigoDelSocio(id));
		// Con un código equivocado, no.
		assertThatThrownBy(() -> como(e.socia(), () -> espera.aprobarAhora(id, EscenarioDirecta.equivocado(jdbc, derivador,
				pedido)))).isInstanceOf(ReglaNegocioException.class);
		como(e.socia(), () -> espera.aprobarAhora(id, EscenarioDirecta.escrito(jdbc, derivador, pedido)));
		assertThat(estado(id)).isEqualTo("VIGENTE");
		assertThat(jdbc.queryForObject("SELECT codigo_mensaje_id FROM conector_colegio WHERE id = ?", Long.class, id))
				.isEqualTo(pedido.mensajeId());
	}

	@Test
	void elOseEntraAlInstanteConElCodigo() {
		Long id = directo(TipoConector.OSE, CambioConectorTest.oseNubefact("nubefact-token-secreto-0123456789-QWER"));
		assertThat(estado(id)).isEqualTo("VIGENTE");
		assertThat(EscenarioDirecta.destinos(jdbc, "ACCION_DIRECTA")).contains(CORREO_CONTROL);
	}
}
