package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Los datos de demostración (perfil {@code dev}, H2 en memoria) actúan como una persona de la demo que acaba de
 * ingresar (sprint 7, tanda 2): se abre su sesión de la base por la ruta de identidad, como en el ingreso, y su secreto
 * queda disponible para {@link FirmaSesion} solo mientras dura la operación. Así las aprobaciones de la demo se firman
 * igual que las de una petición y la firma sigue exigiendo una sesión real de esa persona.
 * <p>
 * Solo funciona con los perfiles {@code dev} o {@code test}: en prod y piloto no hay datos de demostración y el secreto
 * de una sesión sale únicamente de la sesión HTTP.
 */
@Component
public class PersonaDemo {

	/** Desde dónde «ingresa» la persona de la demo (queda en sesion_usuario.ip). */
	static final String IP_DEMO = "127.0.0.1";

	private final UsuarioRepository usuarios;

	private final SesionesFirmadas sesiones;

	private final TransactionTemplate lectura;

	private final Clock reloj;

	private final boolean permitido;

	public PersonaDemo(UsuarioRepository usuarios, SesionesFirmadas sesiones, PlatformTransactionManager transacciones,
			Clock reloj, Environment entorno) {
		this.usuarios = usuarios;
		this.sesiones = sesiones;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.reloj = reloj;
		List<String> perfiles = Arrays.asList(entorno.getActiveProfiles());
		this.permitido = (perfiles.contains("dev") || perfiles.contains("test")) && !perfiles.contains("prod")
				&& !perfiles.contains("piloto");
	}

	/**
	 * Ejecuta la operación en ese colegio como esa persona de la demo (sin transacción abierta: cada servicio abre la
	 * suya).
	 *
	 * @throws IllegalStateException si la persona no existe o fuera de los perfiles dev y test
	 */
	public <T> T como(long colegioId, String nombreUsuario, Supplier<T> operacion) {
		exigirPerfil();
		UsuarioAutenticado persona = ContextoColegio.en(colegioId, () -> lectura.execute(t -> usuarios
				.findByNombreUsuario(nombreUsuario).filter(u -> u.isActivo())
				.map(u -> UsuarioAutenticado.de(u, LocalDateTime.now(reloj))).orElse(null)));
		if (persona == null) {
			throw new IllegalStateException("No existe la persona de la demo «" + nombreUsuario + "» (activa) en el "
					+ "colegio " + colegioId);
		}
		return ContextoColegio.en(colegioId, () -> como(persona, operacion));
	}

	/** Si existen (activas) todas esas personas de la demo en el colegio (si no, la demo no se crea). */
	public boolean existen(long colegioId, String... nombresUsuario) {
		exigirPerfil();
		return Boolean.TRUE.equals(ContextoColegio.en(colegioId, () -> lectura.execute(t -> Arrays.stream(nombresUsuario)
				.allMatch(n -> usuarios.findByNombreUsuario(n).filter(u -> u.isActivo()).isPresent()))));
	}

	/** Ejecuta la operación como esa persona, en el colegio que ya fijó quien llama. */
	public <T> T como(UsuarioAutenticado persona, Supplier<T> operacion) {
		exigirPerfil();
		Objects.requireNonNull(persona, "persona");
		SesionAbierta sesion = sesiones.abrir(persona.colegioId(), persona.usuarioId(), IP_DEMO);
		SecurityContext anterior = SecurityContextHolder.getContext();
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(persona, null,
				persona.getAuthorities()));
		SecurityContextHolder.setContext(contexto);
		TokenDeSesionHttp.fijarDemo(sesion);
		try {
			return operacion.get();
		}
		finally {
			TokenDeSesionHttp.olvidarDemo();
			SecurityContextHolder.setContext(anterior);
			sesiones.cerrar(sesion, MotivoCierreSesion.SALIO);
		}
	}

	private void exigirPerfil() {
		if (!permitido) {
			throw new IllegalStateException("Las personas de la demo solo existen con los perfiles dev y test");
		}
	}
}
