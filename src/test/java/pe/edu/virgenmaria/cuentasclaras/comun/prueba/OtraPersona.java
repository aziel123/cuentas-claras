package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

/**
 * Pruebas que no tratan de las aprobaciones: otra persona (de Promotoría y Dirección) aprueba las solicitudes pendientes
 * y la sesión vuelve a quien las pidió.
 */
public final class OtraPersona {

	public static final UsuarioAutenticado APROBADOR = UsuariosDePrueba.autenticado(1L, 90L, "aprobador.prueba",
			"Aprobador de prueba", false, java.util.EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR));

	private OtraPersona() {
	}

	/** Aprueba la solicitud pendiente de esa entidad (en una base compartida, como MySQL, no toca las demás). */
	public static void apruebaLaDe(BandejaAprobaciones bandeja, JdbcTemplate jdbc, String entidad, Long entidadId) {
		Long id = jdbc.queryForObject("SELECT id FROM solicitud_cambio WHERE estado = 'PENDIENTE' AND entidad = ? "
				+ "AND entidad_id = ?", Long.class, entidad, entidadId);
		Authentication quienPidio = SecurityContextHolder.getContext().getAuthentication();
		try {
			UsuariosDePrueba.iniciarSesion(APROBADOR);
			bandeja.aprobar(id, null);
		}
		finally {
			SecurityContextHolder.getContext().setAuthentication(quienPidio);
		}
	}

	/** Aprueba todas las solicitudes pendientes del colegio 1, en orden. */
	public static void apruebaLoPendiente(BandejaAprobaciones bandeja, JdbcTemplate jdbc) {
		Authentication quienPidio = SecurityContextHolder.getContext().getAuthentication();
		try {
			UsuariosDePrueba.iniciarSesion(APROBADOR);
			for (Long id : jdbc.queryForList("SELECT id FROM solicitud_cambio WHERE estado = 'PENDIENTE' "
					+ "AND colegio_id = 1 ORDER BY id", Long.class)) {
				bandeja.aprobar(id, null);
			}
		}
		finally {
			SecurityContextHolder.getContext().setAuthentication(quienPidio);
		}
	}
}
