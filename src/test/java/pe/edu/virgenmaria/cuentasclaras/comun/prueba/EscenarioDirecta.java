package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sprint 11, tanda 1c (sección 3.16 y 3.16b): el equipo del colegio 1 para las pruebas de «la Promotoría aplica directo».
 * Dos Promotorías con más de 30 días en el rol (la que actúa y su socia), Dirección, Administración y un docente, todas
 * cuentas reales con su celular; y el correo externo de control registrado.
 */
public final class EscenarioDirecta {

	public static final String CORREO_CONTROL = "contador.directa@estudio.pe";

	public static final String MOTIVO = "Lo decidió la Promotoría en la reunión de hoy";

	private EscenarioDirecta() {
	}

	public record Equipo(Usuario promotora, Usuario socia, Usuario directora, Usuario administracion, Usuario docente) {
	}

	public static Equipo preparar(UsuarioRepository usuarios, PasswordEncoder codificador, JdbcTemplate jdbc, Clock reloj) {
		jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en, creado_por) VALUES (1, "
				+ "'huella_correo_externo', ?, NOW(), 'operador.pruebas')", CORREO_CONTROL);
		Usuario promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora.dir", UsuariosDePrueba.CLAVE,
				false, Rol.PROMOTOR);
		Usuario socia = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "socia.dir", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		Usuario directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora.dir", UsuariosDePrueba.CLAVE,
				false, Rol.DIRECTOR);
		Usuario administracion = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "admin.dir", UsuariosDePrueba.CLAVE,
				false, Rol.ADMINISTRACION);
		Usuario docente = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "docente.dir", UsuariosDePrueba.CLAVE, false,
				Rol.DOCENTE);
		LocalDateTime antes = LocalDateTime.now(reloj).minusDays(40);
		for (Usuario u : List.of(promotora, socia, directora, administracion, docente)) {
			// Nadie del equipo es «recién creado» (A5): sus cuentas son de hace 40 días.
			jdbc.update("UPDATE usuario SET creado_en = ?, creado_por = 'operador.pruebas' WHERE id = ?", antes, u.getId());
			jdbc.update("UPDATE usuario_rol SET desde = ? WHERE usuario_id = ?", antes, u.getId());
		}
		return new Equipo(recargar(usuarios, promotora), recargar(usuarios, socia), recargar(usuarios, directora),
				recargar(usuarios, administracion), recargar(usuarios, docente));
	}

	public static Usuario recargar(UsuarioRepository usuarios, Usuario u) {
		return RutaConexion.identidad(() -> pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(1L,
				() -> usuarios.findById(u.getId()).orElseThrow()));
	}

	/** Desde cuándo esa cuenta tiene ese rol (A1c-2: la antigüedad de una Promotoría). */
	public static void rolDesde(JdbcTemplate jdbc, Usuario u, Rol rol, LocalDateTime desde) {
		jdbc.update("UPDATE usuario_rol SET desde = ? WHERE usuario_id = ? AND rol = ?", desde, u.getId(), rol.name());
	}

	/** Corre {@code accion} con {@code quien} en sesión y la cierra al terminar. */
	public static <T> T como(Usuario quien, Supplier<T> accion) {
		UsuariosDePrueba.iniciarSesion(quien);
		try {
			return accion.get();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	public static void como(Usuario quien, Runnable accion) {
		como(quien, () -> {
			accion.run();
			return null;
		});
	}

	/** El código que llegó por WhatsApp para ese pedido, tal como lo escribiría la persona. */
	public static CodigoPromotoria.Escrito escrito(JdbcTemplate jdbc, DerivadorSecreto derivador,
			CodigoPromotoria.Pedido pedido) {
		return CodigosDePrueba.escrito(jdbc, derivador, 1L, pedido);
	}

	/** Lo mismo con un código equivocado. */
	public static CodigoPromotoria.Escrito equivocado(JdbcTemplate jdbc, DerivadorSecreto derivador,
			CodigoPromotoria.Pedido pedido) {
		return new CodigoPromotoria.Escrito(pedido.mensajeId(),
				CodigosDePrueba.otro(CodigosDePrueba.codigo(jdbc, derivador, 1L, pedido.mensajeId())));
	}

	public static long eventos(JdbcTemplate jdbc, String accion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = ?", Long.class, accion);
	}

	/** Los destinos de los mensajes de esa plantilla (WhatsApp de cada Promotoría, correo de control...). */
	public static List<String> destinos(JdbcTemplate jdbc, String plantilla) {
		return jdbc.queryForList("SELECT destino FROM mensaje WHERE plantilla = ? ORDER BY id", String.class, plantilla);
	}

	/** Las cuentas del personal que recibieron un mensaje de esa plantilla. */
	public static List<Long> avisados(JdbcTemplate jdbc, String plantilla) {
		return jdbc.queryForList("SELECT DISTINCT usuario_id FROM mensaje WHERE plantilla = ? AND usuario_id IS NOT NULL",
				Long.class, plantilla);
	}
}
