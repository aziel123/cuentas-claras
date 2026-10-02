package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Carga el usuario que intenta ingresar. El login no pide colegio (el nombre de usuario es único
 * en la plataforma), por eso busca con {@link ContextoColegio#comoSistema}: es una de las pocas
 * clases autorizadas (regla ArchUnit). No es {@code @Transactional}: cambiar de colegio con una
 * transacción abierta no está permitido.
 */
@Service
public class ServicioDetallesUsuario implements UserDetailsService {

	private final UsuarioRepository usuarios;

	private final Clock reloj;

	public ServicioDetallesUsuario(UsuarioRepository usuarios, Clock reloj) {
		this.usuarios = usuarios;
		this.reloj = reloj;
	}

	@Override
	public UserDetails loadUserByUsername(String nombreUsuario) {
		String normalizado = normalizar(nombreUsuario);
		Usuario usuario = buscar(normalizado)
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
		return UsuarioAutenticado.de(usuario, LocalDateTime.now(reloj));
	}

	/** Colegio de un usuario, para auditar intentos de ingreso. Vacío si no existe. */
	public Optional<Long> colegioDe(String nombreUsuario) {
		return buscar(normalizar(nombreUsuario)).map(Usuario::getColegioId);
	}

	/** Minúsculas y sin espacios alrededor, como se guarda. Vacío si no se escribió nada. */
	public static String normalizar(String nombreUsuario) {
		return nombreUsuario == null ? "" : nombreUsuario.trim().toLowerCase(Locale.ROOT);
	}

	private Optional<Usuario> buscar(String normalizado) {
		if (normalizado.isEmpty()) {
			return Optional.empty();
		}
		return ContextoColegio.comoSistema(() -> usuarios.findByNombreUsuario(normalizado));
	}
}
