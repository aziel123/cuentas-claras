package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import javax.sql.DataSource;

/**
 * Cuentas de Promotoría y Dirección en MySQL real (sprint 7, tanda 2). Allí trg_usuario_rol_alta solo deja dar PROMOTOR o
 * DIRECTOR con una solicitud CAMBIO_ROLES aprobada y firmada por OTRA persona (salvo el primer PROMOTOR y el primer
 * DIRECTOR de un colegio). Las pruebas lo hacen por el camino legítimo: dos cuentas base por colegio
 * ({@code base.promotor.cN}, el primer PROMOTOR, y {@code base.director.cN}, el primer DIRECTOR) piden y aprueban los roles
 * de cada cuenta nueva con {@link ServicioUsuarios} y {@link BandejaAprobaciones}. En H2 (sin triggers) no se usa:
 * {@link UsuariosDePrueba#guardar} inserta directo.
 */
@Component
@Profile("test")
public class CuentasDePrueba implements InitializingBean {

	private static volatile CuentasDePrueba enMySql;

	private final UsuarioRepository usuarios;

	private final PasswordEncoder codificador;

	private final ServicioUsuarios servicio;

	private final BandejaAprobaciones bandeja;

	private final SolicitudCambioRepository solicitudes;

	private final boolean separadas;

	public CuentasDePrueba(UsuarioRepository usuarios, PasswordEncoder codificador, ServicioUsuarios servicio,
			BandejaAprobaciones bandeja, SolicitudCambioRepository solicitudes, DataSource fuente) {
		this.usuarios = usuarios;
		this.codificador = codificador;
		this.servicio = servicio;
		this.bandeja = bandeja;
		this.solicitudes = solicitudes;
		this.separadas = fuente instanceof FuenteDatosEnrutada enrutada && enrutada.separadas();
	}

	@Override
	public void afterPropertiesSet() {
		if (separadas) {
			enMySql = this;
		}
	}

	/** La instancia de MySQL real, o {@code null} con H2. */
	static CuentasDePrueba enMySql() {
		return enMySql;
	}

	/**
	 * Da los roles de Promotoría o Dirección de {@code roles} a la cuenta (que ya existe con un rol del personal) por el
	 * camino legítimo: la base de Promotoría lo pide y la base de Dirección lo aprueba (con su firma de sesión).
	 */
	Usuario conRolesDirectivos(long colegioId, Usuario cuenta, Set<Rol> roles) {
		SecurityContext anterior = SecurityContextHolder.getContext();
		try {
			Usuario promotor = base(colegioId, "base.promotor.c" + colegioId, Rol.PROMOTOR);
			Usuario director = base(colegioId, "base.director.c" + colegioId, Rol.DIRECTOR);
			if (cuenta.getId().equals(promotor.getId()) || cuenta.getId().equals(director.getId())) {
				return cuenta;
			}
			ponerSesion(promotor);
			servicio.cambiarRoles(cuenta.getId(), new CambiarRolesRequest(EnumSet.copyOf(roles),
					"Cuenta de prueba de MySQL real"));
			SolicitudCambio solicitud = ContextoColegio.en(colegioId, () -> solicitudes
					.findByEntidadAndEntidadIdAndEstadoOrderByIdAsc("usuario", cuenta.getId(), EstadoSolicitud.PENDIENTE)
					.stream().filter(s -> s.getTipo() == TipoSolicitud.CAMBIO_ROLES).max(Comparator.comparing(
							SolicitudCambio::getId)).orElseThrow());
			ponerSesion(director);
			bandeja.aprobar(solicitud.getId(), "Aprobado en la prueba de MySQL real");
			return ContextoColegio.en(colegioId, () -> usuarios.findById(cuenta.getId()).orElseThrow());
		}
		finally {
			SecurityContextHolder.setContext(anterior);
		}
	}

	/** Las personas inventadas ya llevadas a una cuenta real (colegio:nombre:roles). */
	private final java.util.Map<String, UsuarioAutenticado> reales = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Sprint 7, tanda 2: en MySQL real, una persona inventada de las pruebas (por ejemplo {@code EscenarioCobranza.DIRECCION},
	 * con un id que en una base compartida puede ser de otra cuenta) se lleva a una cuenta REAL con su nombre y sus roles:
	 * así abre su sesión de la base y firma como ella (trg_firma_operacion_nace y cc_firma_valida comparan el id y el
	 * nombre). Si la cuenta con ese nombre tiene otros roles, usa una con el nombre y los roles. Dentro de una transacción
	 * o si la combinación de roles no existe en la base (la rechaza trg_usuario_rol_alta), la deja como está.
	 */
	UsuarioAutenticado real(UsuarioAutenticado persona) {
		if (persona.apoderadoId() != null || persona.roles().isEmpty() || persona.roles().contains(Rol.APODERADO)
				|| org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
			return persona;
		}
		String clave = persona.colegioId() + ":" + persona.getUsername() + ":" + new java.util.TreeSet<>(persona.roles());
		UsuarioAutenticado conocida = reales.get(clave);
		if (conocida != null) {
			return conocida;
		}
		long colegio = persona.colegioId();
		Usuario cuenta = ContextoColegio.en(colegio, () -> usuarios.findByNombreUsuario(persona.getUsername()).orElse(null));
		if (cuenta != null && cuenta.getId().equals(persona.usuarioId())) {
			return persona;
		}
		String nombre = persona.getUsername();
		if (cuenta != null && !cuenta.getRoles().containsAll(persona.roles())) {
			nombre = nombre + "." + persona.roles().stream().map(r -> r.name().toLowerCase(java.util.Locale.ROOT)).sorted()
					.collect(java.util.stream.Collectors.joining("-"));
			String buscado = nombre;
			cuenta = ContextoColegio.en(colegio, () -> usuarios.findByNombreUsuario(buscado).orElse(null));
		}
		if (cuenta == null) {
			SecurityContext anterior = SecurityContextHolder.getContext();
			// Sin la persona en sesión: si no, la cuenta nueva quedaría «preparada» por ella y la regla de quien preparó la
			// cuenta (ControlParticipantes) la trataría como participante de lo que esa persona hace.
			SecurityContextHolder.clearContext();
			try {
				cuenta = UsuariosDePrueba.guardar(usuarios, codificador, colegio, nombre, UsuariosDePrueba.CLAVE, false,
						persona.roles().toArray(Rol[]::new));
			}
			catch (RuntimeException combinacionImposible) {
				return persona;
			}
			finally {
				SecurityContextHolder.setContext(anterior);
			}
		}
		UsuarioAutenticado real = new UsuarioAutenticado(cuenta.getId(), colegio, cuenta.getNombreUsuario(),
				persona.nombreCompleto(), null, true, false, persona.debeCambiarClave(), persona.roles());
		reales.put(clave, real);
		return real;
	}

	/** La cuenta base del colegio (la crea la primera vez: el primer PROMOTOR o el primer DIRECTOR del colegio). */
	private Usuario base(long colegioId, String nombre, Rol rol) {
		Usuario existente = ContextoColegio.en(colegioId, () -> usuarios.findByNombreUsuario(nombre).orElse(null));
		if (existente != null) {
			return existente;
		}
		if (rol == Rol.PROMOTOR) {
			return UsuariosDePrueba.insertar(usuarios, codificador, colegioId, nombre, UsuariosDePrueba.CLAVE, false,
					EnumSet.of(Rol.PROMOTOR));
		}
		Usuario docente = UsuariosDePrueba.insertar(usuarios, codificador, colegioId, nombre, UsuariosDePrueba.CLAVE, false,
				EnumSet.of(Rol.DOCENTE));
		Usuario promotor = ContextoColegio.en(colegioId, () -> usuarios.findByNombreUsuario("base.promotor.c" + colegioId)
				.orElseThrow());
		ponerSesion(promotor);
		// El primer DIRECTOR de un colegio con una sola Promotoría: sin solicitud (no hay quién la apruebe).
		servicio.cambiarRoles(docente.getId(), new CambiarRolesRequest(EnumSet.of(Rol.DIRECTOR),
				"Primera cuenta de Dirección de la prueba"));
		return ContextoColegio.en(colegioId, () -> usuarios.findById(docente.getId()).orElseThrow());
	}

	private static void ponerSesion(Usuario usuario) {
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(UsuariosDePrueba.autenticacion(UsuariosDePrueba.autenticado(usuario)));
		SecurityContextHolder.setContext(contexto);
	}
}
