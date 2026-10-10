package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
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

	/**
	 * Sprint 7, tanda 2: en MySQL real la persona inventada pasa a ser una cuenta real con su nombre y sus roles (para que
	 * abra su sesión de la base y firme como ella; ver {@link CuentasDePrueba}). En H2, tal cual.
	 */
	public static Authentication autenticacion(UsuarioAutenticado usuario) {
		CuentasDePrueba mysql = CuentasDePrueba.enMySql();
		UsuarioAutenticado persona = mysql == null ? usuario : mysql.real(usuario);
		return UsernamePasswordAuthenticationToken.authenticated(persona, null, persona.getAuthorities());
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
	 * <p>
	 * Sprint 7, tanda 2: por la ruta de identidad (en MySQL real, la conexión de {@code cc_sistema}); nace con la clave
	 * por cambiar y después la cambia (trg_usuario_nace). En MySQL real, PROMOTOR y DIRECTOR se dan con una solicitud
	 * CAMBIO_ROLES que aprueba otra persona ({@link CuentasDePrueba}); en H2, directo.
	 *
	 * @param temporal {@code true}: su clave es temporal y debe cambiarla al ingresar
	 */
	public static Usuario guardar(UsuarioRepository usuarios, PasswordEncoder codificador, long colegioId,
			String nombreUsuario, String clave, boolean temporal, Rol... roles) {
		Set<Rol> pedidos = EnumSet.copyOf(List.of(roles));
		CuentasDePrueba mysql = CuentasDePrueba.enMySql();
		boolean directivos = pedidos.contains(Rol.PROMOTOR) || pedidos.contains(Rol.DIRECTOR);
		if (mysql == null || !directivos) {
			return insertar(usuarios, codificador, colegioId, nombreUsuario, clave, temporal, pedidos);
		}
		Set<Rol> otros = EnumSet.copyOf(pedidos);
		otros.removeAll(EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR));
		Usuario cuenta = insertar(usuarios, codificador, colegioId, nombreUsuario, clave, temporal,
				otros.isEmpty() ? EnumSet.of(Rol.DOCENTE) : otros);
		return mysql.conRolesDirectivos(colegioId, cuenta, pedidos);
	}

	/** Inserta la cuenta con esos roles por la ruta de identidad (sin solicitud: en MySQL, solo roles del personal). */
	static Usuario insertar(UsuarioRepository usuarios, PasswordEncoder codificador, long colegioId,
			String nombreUsuario, String clave, boolean temporal, Set<Rol> roles) {
		return RutaConexion.identidad(() -> ContextoColegio.en(colegioId, () -> {
			String hash = codificador.encode(clave);
			Usuario usuario = Usuario.nuevo(nombreUsuario, "Nombre de " + nombreUsuario, null, hash, roles);
			// Sprint 5: el personal tiene celular (ahí le llega su enlace). Rango 966 para no chocar con los apoderados.
			usuario.asignarTelefonoWhatsapp(celular(nombreUsuario));
			Usuario guardado = usuarios.saveAndFlush(usuario);
			if (!temporal) {
				guardado.cambiarClave(hash, LocalDateTime.now(RELOJ), false);
				guardado = usuarios.saveAndFlush(guardado);
			}
			return guardado;
		}));
	}

	/** Sprint 5: el celular de prueba de un usuario del personal (+51966XXXXXX, distinto por nombre). */
	public static String celular(String nombreUsuario) {
		return "+51966" + String.format("%06d", Math.floorMod(nombreUsuario.hashCode(), 1_000_000));
	}
}
