package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.proceso.AplicacionSolicitudesEnEspera;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.Equipo;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/** Sprint 11, tanda 1c: lo común de las pruebas de las directas de Promotoría (equipo, reloj, código y esperas). */
@Import(ConfiguracionRelojAjustable.class)
abstract class BaseDirecta {

	@Autowired
	protected BandejaAprobaciones bandeja;

	@Autowired
	protected ServicioUsuarios servicioUsuarios;

	@Autowired
	protected AplicacionSolicitudesEnEspera esperas;

	@Autowired
	protected CodigoPromotoria codigos;

	@Autowired
	protected DerivadorSecreto derivador;

	@Autowired
	protected UsuarioRepository usuarios;

	@Autowired
	protected ColegioRepository colegios;

	@Autowired
	protected PasswordEncoder codificador;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected Clock reloj;

	protected Equipo e;

	@BeforeEach
	void prepararEquipo() {
		((RelojAjustable) reloj).fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		e = EscenarioDirecta.preparar(usuarios, codificador, jdbc, reloj);
	}

	@Autowired
	protected pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil calendarioHabil;

	@AfterEach
	void limpiarEquipo() {
		SecurityContextHolder.clearContext();
		// Los días no laborables que aprobaron estas pruebas no quedan en la caché del calendario (la base se limpia).
		calendarioHabil.invalidarTodo();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	protected void avanzar(Duration cuanto) {
		((RelojAjustable) reloj).avanzar(cuanto);
	}

	/** {@code quien} pide los roles nuevos de {@code cuenta}: devuelve la solicitud. */
	protected Long pedirRoles(Usuario quien, Usuario cuenta, Set<Rol> roles) {
		como(quien, () -> servicioUsuarios.cambiarRoles(cuenta.getId(), new CambiarRolesRequest(roles, MOTIVO)));
		return EscenarioAprobaciones.pendiente(jdbc, "usuario", cuenta.getId());
	}

	/** «Aprobar y aplicar» de quien la pidió, con su código correcto. */
	protected void aplicarConCodigo(Usuario quien, Long solicitud) {
		CodigoPromotoria.Pedido pedido = como(quien, () -> bandeja.pedirCodigo(solicitud));
		como(quien, () -> bandeja.aprobar(solicitud, null, false, List.of(), EscenarioDirecta.escrito(jdbc, derivador,
				pedido)));
	}

	protected List<String> roles(Usuario u) {
		return jdbc.queryForList("SELECT rol FROM usuario_rol WHERE usuario_id = ? ORDER BY rol", String.class, u.getId());
	}

	protected String estado(Long solicitud) {
		return jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud);
	}
}
