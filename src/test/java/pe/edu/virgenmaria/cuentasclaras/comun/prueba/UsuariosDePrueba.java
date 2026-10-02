package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Usuarios para pruebas: en sesión (sin base de datos) o guardados de verdad en la base.
 */
public final class UsuariosDePrueba {

	/** Mismo reloj que la aplicación (hora de Lima), sin depender de la zona horaria de la JVM. */
	private static final java.time.Clock RELOJ = java.time.Clock.system(
			pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo.ZONA_LIMA);

	/** Clave que cumple la política, para los usuarios guardados en la base. */
	public static final String CLAVE = "una clave de prueba larga";

	private UsuariosDePrueba() {
	}

	public static UsuarioAutenticado autenticado(long colegioId, long usuarioId, String nombreUsuario,
			String nombreCompleto, boolean clavePendiente, Set<Rol> roles) {
		return new UsuarioAutenticado(usuarioId, colegioId, nombreUsuario, nombreCompleto, null, true, false,
				clavePendiente, roles);
	}

	public static UsuarioAutenticado autenticado(Rol... roles) {
		return autenticado(1L, 1L, "usuario.prueba", "Usuario de Prueba", false, EnumSet.copyOf(List.of(roles)));
	}

	/** El usuario de la base, como estaría en sesión. */
	public static UsuarioAutenticado autenticado(Usuario usuario) {
		return UsuarioAutenticado.de(usuario, LocalDateTime.now(RELOJ));
	}

	/** Deja al usuario autenticado en el contexto de seguridad del hilo (pruebas de servicios). */
	public static void iniciarSesion(UsuarioAutenticado usuario) {
		org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(autenticacion(usuario));
	}

	/** Deja al usuario de la base en el contexto de seguridad del hilo (pruebas de servicios). */
	public static void iniciarSesion(Usuario usuario) {
		org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(autenticacion(autenticado(usuario)));
	}

	public static RequestPostProcessor como(Usuario usuario) {
		return authentication(autenticacion(autenticado(usuario)));
	}

	public static Authentication autenticacion(UsuarioAutenticado usuario) {
		return UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities());
	}

	/** Para MockMvc: {@code .with(UsuariosDePrueba.como(Rol.CAJA))}. */
	public static RequestPostProcessor como(Rol... roles) {
		return authentication(autenticacion(autenticado(roles)));
	}

	public static RequestPostProcessor como(UsuarioAutenticado usuario) {
		return authentication(autenticacion(usuario));
	}

	/**
	 * Guarda un usuario real en la base, en el colegio indicado.
	 *
	 * @param temporal {@code true}: su clave es temporal y debe cambiarla al ingresar
	 */
	public static Usuario guardar(UsuarioRepository usuarios, PasswordEncoder codificador, long colegioId,
			String nombreUsuario, String clave, boolean temporal, Rol... roles) {
		return ContextoColegio.en(colegioId, () -> {
			String hash = codificador.encode(clave);
			Usuario usuario = Usuario.nuevo(nombreUsuario, "Nombre de " + nombreUsuario, null, hash,
					EnumSet.copyOf(List.of(roles)));
			if (!temporal) {
				usuario.cambiarClave(hash, LocalDateTime.now(RELOJ), false);
			}
			return usuarios.save(usuario);
		});
	}
}
