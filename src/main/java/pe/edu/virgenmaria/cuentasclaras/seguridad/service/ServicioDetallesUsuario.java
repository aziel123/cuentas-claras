package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.Locale;
import java.util.Optional;

/**
 * Busca usuarios por nombre en toda la plataforma (el login no pide colegio; el nombre es único).
 * Usa {@link ContextoColegio#comoSistema}: es una de las pocas clases autorizadas (regla ArchUnit).
 * No es {@code @Transactional}: cambiar de colegio con una transacción abierta no está permitido.
 */
@Service
public class ServicioDetallesUsuario {

	private final UsuarioRepository usuarios;

	public ServicioDetallesUsuario(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	/** Colegio de un usuario (para ingresar y para detectar nombres repetidos). Vacío si no existe. */
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
