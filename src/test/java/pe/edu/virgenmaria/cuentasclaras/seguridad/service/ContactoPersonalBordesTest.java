package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AvisosPromotoriaListos;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QA sprint 6 (P6): bordes del cambio de celular o correo del personal.
 * <ul>
 *   <li>Dada la solicitud de cambio del celular de la promotora, cuando Administración intenta aprobarla, entonces se
 *       rechaza por permisos y nada cambia.</li>
 *   <li>Dado un docente, cuando pide su propio cambio, entonces puede; cuando pide el de la cajera, no.</li>
 *   <li>Dada una solicitud pendiente, cuando la cuenta se desactiva antes de aprobarla, entonces no se aplica.</li>
 *   <li>Dado el cambio aprobado, cuando sale la siguiente alerta, entonces llega al celular nuevo y no al anterior.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ContactoPersonalBordesTest {

	private static final String MOTIVO = "Cambió de número de celular este mes";

	@Autowired
	private ServicioContactoPersonal servicio;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private ApplicationEventPublisher eventos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario promotora;

	private Usuario directora;

	private Usuario lucia;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		promotora = guardar("promotora", Rol.PROMOTOR);
		directora = guardar("directora", Rol.DIRECTOR);
		lucia = guardar("lucia.caja", Rol.CAJA);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Usuario guardar(String nombre, Rol... roles) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, roles);
	}

	private String celularDe(Usuario u) {
		return jdbc.queryForObject("SELECT telefono_whatsapp FROM usuario WHERE id = ?", String.class, u.getId());
	}

	@Test
	void administracionNoApruebaElCambioDeCelularDeLaPromotora() {
		String anterior = celularDe(promotora);
		UsuariosDePrueba.iniciarSesion(promotora);
		Long solicitud = servicio.solicitar(promotora.getId(), "987 444 555", null, MOTIVO);
		Usuario administracion = guardar("administracion.uno", Rol.ADMINISTRACION);
		UsuariosDePrueba.iniciarSesion(administracion);

		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(AccessDeniedException.class);
		assertThat(celularDe(promotora)).isEqualTo(anterior);
		assertThat(jdbc.queryForObject("SELECT estado FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("PENDIENTE");
	}

	@Test
	void unDocentePideSuPropioCambioPeroNoElDeLaCajera() {
		Usuario docente = guardar("docente.uno", Rol.DOCENTE);
		UsuariosDePrueba.iniciarSesion(docente);
		assertThat(servicio.solicitar(docente.getId(), "987 444 666", null, MOTIVO)).isNotNull();
		assertThatThrownBy(() -> servicio.solicitar(lucia.getId(), "987 444 777", null, MOTIVO))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio WHERE tipo = 'CAMBIO_CONTACTO_PERSONAL'",
				Long.class)).isEqualTo(1);
	}

	@Test
	void noDebeAplicarseElCambioDeUnaCuentaQueSeDesactivoAntesDeAprobarlo() {
		String anterior = celularDe(lucia);
		UsuariosDePrueba.iniciarSesion(lucia);
		Long solicitud = servicio.solicitar(lucia.getId(), "987 444 888", null, MOTIVO);
		jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", lucia.getId());

		UsuariosDePrueba.iniciarSesion(directora);
		assertThatThrownBy(() -> bandeja.aprobar(solicitud, null)).isInstanceOf(ReglaNegocioException.class);
		assertThat(celularDe(lucia)).isEqualTo(anterior);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CONTACTO_PERSONAL_CAMBIADO'",
				Long.class)).isZero();
	}

	@Test
	void laSiguienteAlertaLlegaAlCelularNuevoYNoAlAnterior() {
		LocalDate jueves = LocalDate.of(2027, 4, 15);
		reloj.fijar(jueves.atTime(9, 0).atZone(reloj.getZone()).toInstant());
		String anterior = celularDe(promotora);
		UsuariosDePrueba.iniciarSesion(promotora);
		Long solicitud = servicio.solicitar(promotora.getId(), "987 444 999", null, MOTIVO);
		UsuariosDePrueba.iniciarSesion(directora);
		bandeja.aprobar(solicitud, null);
		SecurityContextHolder.clearContext();

		EjecucionComoSistema.como(ActorSistema.PANEL, 1L, () -> new TransactionTemplate(transacciones)
				.executeWithoutResult(t -> eventos.publishEvent(new AvisosPromotoriaListos(1L, jueves,
						List.of(new Aviso(TipoAviso.CIERRE_CON_DIFERENCIA, "C:1", "-S/ 20.00"))))));

		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'ALERTA_PROMOTORIA' AND usuario_id = ?",
				String.class, promotora.getId())).containsExactly("+51987444999").doesNotContain(anterior);
	}
}
