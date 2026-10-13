package pe.edu.virgenmaria.cuentasclaras.plataforma;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comun.config.PropiedadesPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.inicial.InicializadorOperadores;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.GeneradorClaveTemporal;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 8, tanda 1 (sección 3.2, E8): {@code CC_OPERADORES} decide quién es operador. Al arrancar se crea al que falta
 * (en el colegio de plataforma, con su enlace al correo y sin clave en el entorno) y se desactiva al que salió de la lista,
 * con sus sesiones cerradas. Queda en la bitácora de la plataforma.
 */
@PruebaIntegracion
class InicializadorOperadoresTest {

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioService colegioService;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private GeneradorClaveTemporal generador;

	@Autowired
	private AuditoriaService auditoria;

	@Autowired
	private EjecucionIdentidad identidad;

	@Autowired
	private SesionesFirmadas sesiones;

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private Clock reloj;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@BeforeEach
	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private InicializadorOperadores con(String lista) {
		return new InicializadorOperadores(usuarios, colegioService, codificador, generador, auditoria, identidad, sesiones,
				eventos, new PropiedadesPlataforma("COMPARTIDA", lista, 25, 8000), transacciones, reloj);
	}

	@Test
	void creaAlQueFaltaYDesactivaAlQueSaleDeLaLista() {
		long plataforma = EscenarioPlataforma.plataformaId(colegios);

		var primero = con("ana.ops:ana@proveedor.pe, beto.ops:beto@proveedor.pe").sincronizar();

		assertThat(primero.creados()).containsExactly("ana.ops", "beto.ops");
		Map<String, Object> ana = jdbc.queryForMap("SELECT u.colegio_id, u.activo, u.debe_cambiar_clave, u.creado_por, r.rol "
				+ "FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id WHERE u.nombre_usuario = 'ana.ops'");
		assertThat(ana).containsEntry("colegio_id", plataforma).containsEntry("activo", true)
				.containsEntry("debe_cambiar_clave", true).containsEntry("rol", "OPERADOR_PLATAFORMA")
				.containsEntry("creado_por", "sistema.plataforma");
		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'ACTIVACION_CUENTA' AND colegio_id = ?",
				String.class, plataforma)).containsExactlyInAnyOrder("ana@proveedor.pe", "beto@proveedor.pe");

		// Sin cambios en la lista, nada cambia.
		assertThat(con("ana.ops:ana@proveedor.pe,beto.ops:beto@proveedor.pe").sincronizar().creados()).isEmpty();

		// Beto sale de la lista: queda inactivo y auditado.
		var segundo = con("ana.ops:ana@proveedor.pe").sincronizar();
		assertThat(segundo.desactivados()).containsExactly("beto.ops");
		assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE nombre_usuario = 'beto.ops'", Boolean.class)).isFalse();
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, plataforma))
				.contains("OPERADOR_CREADO", "OPERADOR_DESACTIVADO");

		// Vuelve a la lista: se reactiva con un enlace nuevo.
		assertThat(con("ana.ops:ana@proveedor.pe,beto.ops:beto@proveedor.pe").sincronizar().reactivados())
				.containsExactly("beto.ops");
	}

	@Test
	void unaListaMalEscritaNoArranca() {
		assertThatThrownBy(() -> new PropiedadesPlataforma("COMPARTIDA", "sin-correo", 25, 8000))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("usuario:correo");
		assertThatThrownBy(() -> new PropiedadesPlataforma("COMPARTIDA", "a.b:a@b.pe,a.b:c@d.pe", 25, 8000))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repite");
		assertThatThrownBy(() -> new PropiedadesPlataforma("OTRA", "", 25, 8000))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("COMPARTIDA");
	}
}
