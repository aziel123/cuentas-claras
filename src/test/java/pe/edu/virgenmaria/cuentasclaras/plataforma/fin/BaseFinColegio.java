package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.MotivoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.exportacion.proceso.EntregaPublica;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.AltaColegioRequest;
import pe.edu.virgenmaria.cuentasclaras.plataforma.proceso.AplicacionCambiosEstado;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.CambiosEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.FinDeColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2: lo común de las pruebas del fin de un colegio (H2): el alta con un titular con correo y celular, la
 * suspensión aplicada (que genera la exportación), el enlace y el código que le llegaron al titular, y el cierre.
 */
abstract class BaseFinColegio {

	static final String MOTIVO_SUSPENSION = "Tres meses sin pagar la suscripción, con aviso escrito.";

	static final String REFERENCIA = "Carta notarial 2026-118";

	@Autowired
	AltaColegio alta;

	@Autowired
	CambiosEstadoColegio cambios;

	@Autowired
	AplicacionCambiosEstado aplicacion;

	@Autowired
	FinDeColegio fin;

	@Autowired
	EntregaPublica entrega;

	@Autowired
	DerivadorSecreto derivador;

	@Autowired
	SecretosConectores secretos;

	@Autowired
	UsuarioRepository usuarios;

	@Autowired
	ColegioRepository colegios;

	@Autowired
	PasswordEncoder codificador;

	@Autowired
	TokenDeSesionDePrueba tokens;

	@Autowired
	PlatformTransactionManager transacciones;

	@Autowired
	RelojAjustable reloj;

	@Autowired
	JdbcTemplate jdbc;

	Usuario ana;

	Usuario beto;

	@BeforeEach
	void prepararBase() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		ana = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.ana");
		beto = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.beto");
	}

	@AfterEach
	void limpiarBase() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
	}

	/** Un colegio en producción con su titular (correo y celular) activado. */
	long colegioEnProduccion(String nombre, String ruc, String titular, String dni, String celular) {
		como(ana);
		long id = alta.crear(new AltaColegioRequest(nombre, "Asociación " + nombre + " S.A.C.", ruc,
				"Av. Los Educadores 123, Lima", "contacto@" + titular.replace('.', '-') + ".edu.pe",
				EnumSet.of(Nivel.PRIMARIA), "CT-" + titular, "Titular " + nombre, dni, titular, celular,
				titular.replace('.', '-') + "@correo.pe"), null, null).colegioId();
		SecurityContextHolder.clearContext();
		jdbc.update("UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = ?, produccion_por = 'prueba' WHERE id = ?",
				LocalDateTime.now(reloj), id);
		activar(id, titular);
		// El correo externo de control del colegio (lo registra el operador): recibe las huellas y los movimientos.
		jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en, creado_por) VALUES (?, "
				+ "'huella_correo_externo', ?, ?, 'operador.ana')", id, "control@" + titular.replace('.', '-') + ".pe",
				LocalDateTime.now(reloj));
		return id;
	}

	/** Dos operadores suspenden; a los 32 días sistema.plataforma la aplica y sistema.exportacion genera la exportación. */
	void suspender(long colegio) {
		como(ana);
		Long id = cambios.proponer(colegio, TipoCambioEstado.SUSPENSION, MotivoCambioEstado.FALTA_PAGO, MOTIVO_SUSPENSION,
				REFERENCIA);
		como(beto);
		cambios.aprobar(colegio, id);
		SecurityContextHolder.clearContext();
		reloj.mover(Duration.ofDays(32));
		assertThat(aplicacion.enColegio(colegio)).isEqualTo(1);
		assertThat(estado(colegio)).isEqualTo("SUSPENDIDO");
	}

	/** El cierre, por dos operadores. */
	void cerrar(long colegio) {
		como(ana);
		Long id = cambios.proponer(colegio, TipoCambioEstado.CIERRE, MotivoCambioEstado.FALTA_PAGO,
				"El colegio no pagó y recibió su exportación: se cierra.", REFERENCIA);
		como(beto);
		cambios.aprobar(colegio, id);
		SecurityContextHolder.clearContext();
	}

	Map<String, Object> exportacion(long colegio) {
		return jdbc.queryForMap("SELECT * FROM exportacion_colegio WHERE colegio_id = ? ORDER BY id DESC LIMIT 1", colegio);
	}

	/** El enlace que le llegó al titular por correo (el último). */
	String token(long colegio) {
		String clave = jdbc.queryForObject("SELECT clave FROM mensaje WHERE colegio_id = ? AND tipo = 'EXPORTACION_ENLACE' "
				+ "ORDER BY id DESC LIMIT 1", String.class, colegio);
		return derivador.tokenExportacion(colegio, clave);
	}

	/** El código que le llegó al titular por WhatsApp (el último). */
	String codigo(long colegio) {
		String clave = jdbc.queryForObject("SELECT clave FROM mensaje WHERE colegio_id = ? AND tipo = 'CODIGO_EXPORTACION' "
				+ "ORDER BY id DESC LIMIT 1", String.class, colegio);
		return derivador.codigoExportacion(colegio, clave);
	}

	/** El titular recibe la exportación: DNI, código, la clave (una vez) y una descarga. Devuelve la clave. */
	String recibir(long colegio, String dni) {
		String token = token(colegio);
		entrega.confirmarDni(token, dni);
		String codigo = codigo(colegio);
		String clave = entrega.confirmarCodigo(token, codigo);
		entrega.descargar(token, codigo, "203.0.113.7");
		return clave;
	}

	String estado(long colegio) {
		return jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, colegio);
	}

	void como(Usuario usuario) {
		SecurityContextHolder.clearContext();
		UsuariosDePrueba.iniciarSesion(usuario);
	}

	Usuario activar(long colegio, String nombre) {
		return ContextoColegio.en(colegio, () -> new TransactionTemplate(transacciones).execute(t -> {
			Usuario u = usuarios.findByNombreUsuario(nombre).orElseThrow();
			u.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), LocalDateTime.now(reloj), false);
			return usuarios.saveAndFlush(u);
		}));
	}

	/** Una sesión abierta en la base (en H2 la abre la firma de prueba). */
	void ingresar(Usuario usuario) {
		como(usuario);
		new TransactionTemplate(transacciones).executeWithoutResult(t -> tokens.actual());
	}

	Usuario usuario(long colegio, String nombre) {
		return ContextoColegio.en(colegio, () -> new TransactionTemplate(transacciones).execute(t -> usuarios
				.findByNombreUsuario(nombre).orElseThrow()));
	}
}
