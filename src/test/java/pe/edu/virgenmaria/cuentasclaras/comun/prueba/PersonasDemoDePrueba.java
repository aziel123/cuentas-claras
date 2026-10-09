package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.Map;

/**
 * Las personas de la demo (las que crea {@code DatosDemoDev} en el perfil dev) para las pruebas de los datos de
 * demostración: desde el sprint 7 (tanda 2) actúan con su sesión de la base ({@code PersonaDemo}), así que deben existir.
 */
public final class PersonasDemoDePrueba {

	private static final Map<String, Rol> ROLES = Map.of("promotor", Rol.PROMOTOR, "director", Rol.DIRECTOR,
			"administracion", Rol.ADMINISTRACION, "caja", Rol.CAJA, "caja2", Rol.CAJA);

	private PersonasDemoDePrueba() {
	}

	/** Crea en el colegio principal las personas que falten (por nombre de usuario). */
	public static void asegurar(UsuarioRepository usuarios, PasswordEncoder codificador, JdbcTemplate jdbc,
			String... nombres) {
		for (String nombre : nombres) {
			Rol rol = ROLES.get(nombre);
			if (rol == null) {
				throw new IllegalArgumentException("No es una persona de la demo: " + nombre);
			}
			Long hay = jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = ? AND colegio_id = ?",
					Long.class, nombre, DatosDemoDev.COLEGIO_PRINCIPAL);
			if (hay == null || hay == 0) {
				UsuariosDePrueba.guardar(usuarios, codificador, DatosDemoDev.COLEGIO_PRINCIPAL, nombre,
						UsuariosDePrueba.CLAVE, false, rol);
			}
		}
	}
}
