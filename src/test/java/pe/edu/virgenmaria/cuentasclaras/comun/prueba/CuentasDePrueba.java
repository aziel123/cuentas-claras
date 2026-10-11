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
import java.util.List;
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
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
public class CuentasDePrueba implements InitializingBean, org.springframework.boot.ApplicationRunner {

	private static volatile CuentasDePrueba enMySql;

	private final UsuarioRepository usuarios;

	private final PasswordEncoder codificador;

	private final ServicioUsuarios servicio;

	private final BandejaAprobaciones bandeja;

	private final SolicitudCambioRepository solicitudes;

	private final boolean separadas;

	private final org.springframework.beans.factory.ObjectProvider<pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio> alta;

	private final org.springframework.beans.factory.ObjectProvider<pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion> produccion;

	private final pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository colegios;

	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	public CuentasDePrueba(UsuarioRepository usuarios, PasswordEncoder codificador, ServicioUsuarios servicio,
			BandejaAprobaciones bandeja, SolicitudCambioRepository solicitudes, DataSource fuente,
			org.springframework.beans.factory.ObjectProvider<pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio> alta,
			org.springframework.beans.factory.ObjectProvider<pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion> produccion,
			pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository colegios) {
		this.alta = alta;
		this.produccion = produccion;
		this.colegios = colegios;
		this.jdbc = new org.springframework.jdbc.core.JdbcTemplate(fuente);
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

	/** RUC válidos y distintos para los colegios que se ponen en marcha (dos sedes con el mismo RUC no comparten serie). */
	private static final List<String> RUCS = List.of("20600000013", "20600000021", "20600000030", "20600000048",
			"20600000056");

	/**
	 * Sprint 8, tanda 1 (MySQL real): V28 deja en preparación a los colegios de una base nueva (sin cuentas), y en
	 * preparación la base no deja registrar pagos. Antes de las pruebas, cada colegio así se pone en marcha por el camino
	 * legítimo, como en producción: un operador de la plataforma (creado por sistema.plataforma) completa la ficha y da de
	 * alta la primera Promotoría con la firma de su sesión (trg_primera_promotoria_nace); la Promotoría da la primera
	 * Dirección (primera_direccion) y una Caja; y la Promotoría lo pasa a producción con su firma (trg_colegio_cambio). En
	 * la fase 1 del job (sin 02 ni 03) no se hace nada.
	 */
	@Override
	public void run(org.springframework.boot.ApplicationArguments argumentos) {
		if (!separadas || !conPermisosYTriggers()) {
			return;
		}
		SecurityContext anterior = SecurityContextHolder.getContext();
		try {
			int indice = 0;
			for (var colegio : colegios.findByTipoOrderByIdAsc(pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio.COLEGIO)) {
				if (!colegio.enProduccion()) {
					ponerEnMarcha(colegio.getId(), RUCS.get(indice % RUCS.size()));
				}
				indice++;
			}
		}
		finally {
			SecurityContextHolder.setContext(anterior);
		}
	}

	private boolean conPermisosYTriggers() {
		try {
			String lista = jdbc.queryForObject("SELECT triggers_instalados()", String.class);
			return lista != null && lista.contains("trg_colegio_cambio");
		}
		catch (org.springframework.dao.DataAccessException e) {
			return false;
		}
	}

	private void ponerEnMarcha(long colegioId, String ruc) {
		String sufijo = "c" + colegioId;
		Usuario operador = EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.pruebas");
		ponerSesion(operador);
		var servicioAlta = alta.getObject();
		servicioAlta.corregirFicha(colegioId, new pe.edu.virgenmaria.cuentasclaras.plataforma.dto.FichaColegioRequest(
				colegios.findById(colegioId).orElseThrow().getNombre(), "Colegio de pruebas " + sufijo + " S.A.C.", ruc,
				"Av. Pruebas 123, Lima", "colegio." + sufijo + "@pruebas.pe",
				java.util.EnumSet.allOf(pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel.class), "CT-" + sufijo,
				"Titular de pruebas", "4" + String.format("%07d", colegioId)));
		String nombre = "base.promotor." + sufijo;
		if (ContextoColegio.en(colegioId, () -> usuarios.findByNombreUsuario(nombre)).isEmpty()) {
			servicioAlta.completarAlta(colegioId, new pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.AltaTitular
					.DatosTitular(nombre, "Nombre de " + nombre, UsuariosDePrueba.celular(nombre), null));
		}
		// El titular «activa» su cuenta: elige la clave de las pruebas (por la ruta de identidad, como la activación).
		pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion.identidad(() -> ContextoColegio.en(colegioId, () -> {
			Usuario titular = usuarios.findByNombreUsuario(nombre).orElseThrow();
			if (titular.isDebeCambiarClave()) {
				titular.cambiarClave(codificador.encode(UsuariosDePrueba.CLAVE), java.time.LocalDateTime.now(java.time.Clock
						.system(pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo.ZONA_LIMA)), false);
				usuarios.saveAndFlush(titular);
			}
			return null;
		}));
		Usuario promotor = base(colegioId, nombre, Rol.PROMOTOR);
		base(colegioId, "base.director." + sufijo, Rol.DIRECTOR);
		if (ContextoColegio.en(colegioId, () -> usuarios.findByNombreUsuario("base.caja." + sufijo)).isEmpty()) {
			UsuariosDePrueba.insertar(usuarios, codificador, colegioId, "base.caja." + sufijo, UsuariosDePrueba.CLAVE, false,
					java.util.EnumSet.of(Rol.CAJA));
		}
		ponerSesion(promotor);
		produccion.getObject().pasar();
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
			// Sprint 8, tanda 1: el primer PROMOTOR de un colegio lo crea el operador por el alta (ponerEnMarcha), una vez.
			throw new IllegalStateException("El colegio " + colegioId + " no tiene su primera Promotoría: debió ponerse en "
					+ "marcha al arrancar (CuentasDePrueba.run).");
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
