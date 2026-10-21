package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.CorreccionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ResultadoRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.academico.service.AsistenciaDireccion;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConsentimientosAviso;
import pe.edu.virgenmaria.cuentasclaras.academico.service.JustificacionesFamilia;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ServicioAsistencia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.AvisosAsistencia;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sprint 9, tanda 2: la base de las pruebas de asistencia (H2, aplicación completa, reloj ajustable en un martes de
 * clases). Cada prueba parte de {@link EscenarioAsistencia} con la base limpia.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
abstract class PruebaAsistencia {

	@Autowired
	protected ServicioAsistencia asistencia;

	@Autowired
	protected AsistenciaDireccion direccion;

	@Autowired
	protected JustificacionesFamilia justificaciones;

	@Autowired
	protected ConsentimientosAviso consentimientos;

	@Autowired
	protected AvisosAsistencia avisos;

	@Autowired
	protected Asignaciones asignaciones;

	@Autowired
	protected BandejaAprobaciones bandeja;

	@Autowired
	protected ServicioEstructura estructura;

	@Autowired
	protected ServicioAlumnos alumnos;

	@Autowired
	protected UsuarioRepository usuarios;

	@Autowired
	protected PasswordEncoder codificador;

	@Autowired
	protected TokenDeSesionDePrueba tokens;

	@Autowired
	protected RelojAjustable reloj;

	@Autowired
	protected JdbcTemplate jdbc;

	protected EscenarioAsistencia.Datos datos;

	@BeforeEach
	void prepararEscenario() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		reloj.fijar(EscenarioAsistencia.MARTES_8);
		datos = EscenarioAsistencia.preparar(estructura, alumnos, asignaciones, bandeja, usuarios, codificador, jdbc);
	}

	@AfterEach
	void limpiarEscenario() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
	}

	/** Corre la operación como esa persona y deja la sesión vacía. */
	protected <T> T como(UsuarioAutenticado quien, Supplier<T> operacion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return operacion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	protected void como(UsuarioAutenticado quien, Runnable operacion) {
		como(quien, () -> {
			operacion.run();
			return null;
		});
	}

	protected UsuarioAutenticado tutora() {
		return UsuariosDePrueba.autenticado(datos.tutora());
	}

	protected UsuarioAutenticado otroDocente() {
		return UsuariosDePrueba.autenticado(datos.otroDocente());
	}

	/** La tutora registra hoy 5.° A con esos estados (los demás, presentes). */
	protected ResultadoRegistro registrar(Map<Long, EstadoAsistencia> estados) {
		return como(tutora(), () -> asistencia.guardar(datos.seccion(), EscenarioAsistencia.hoja(estados,
				List.of(datos.mateo(), datos.valeria(), datos.lucas()))));
	}

	protected void corregirHoy(Long alumno, EstadoAsistencia estado) {
		Long marca = EscenarioAsistencia.marcaVigente(jdbc, alumno, hoy().toLocalDate());
		como(tutora(), () -> asistencia.corregirHoy(marca, new CorreccionRequest(estado, null,
				EscenarioAsistencia.MOTIVO)));
	}

	/** La hora de Lima de ahora mismo en el reloj de las pruebas. */
	protected LocalDateTime hoy() {
		return LocalDateTime.ofInstant(reloj.instant(), ZoneId.of("America/Lima"));
	}

	/** Fija el reloj en esa hora de Lima del día del reloj (o de otro día, con {@code dias}). */
	protected void alas(int dias, int hora, int minuto) {
		LocalDateTime base = LocalDateTime.of(EscenarioAsistencia.MARTES.plusDays(dias), java.time.LocalTime.of(hora,
				minuto));
		reloj.fijar(base.atZone(ZoneId.of("America/Lima")).toInstant());
	}

	protected void avanzar(Duration duracion) {
		reloj.avanzar(duracion);
	}

	/** La pasada de los avisos de asistencia del colegio 1, a la hora del reloj. */
	protected int pasadaDeAvisos() {
		return avisos.enColegio(1L, hoy()).mensajes();
	}

	protected long mensajes(String tipo) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = ?", Long.class, tipo);
	}

	protected List<String> parametros(String tipo) {
		return jdbc.queryForList("SELECT parametros FROM mensaje WHERE tipo = ? ORDER BY id", String.class, tipo);
	}

	protected static UsuarioAutenticado direccion() {
		return EscenarioCobranza.DIRECCION;
	}
}
