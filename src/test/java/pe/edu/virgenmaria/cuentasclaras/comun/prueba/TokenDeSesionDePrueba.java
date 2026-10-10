package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.SesionUsuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.SesionUsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.TokenDeSesionHttp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El secreto de la sesión en las pruebas (sprint 7, tanda 2; sección 3.4): si la petición tiene una sesión HTTP con su
 * sesión de la base (un ingreso real por MockMvc), usa esa; si no (pruebas de servicios con
 * {@code UsuariosDePrueba.iniciarSesion} o MockMvc con un principal inyectado), abre una sesión de la base para el
 * principal de la prueba, como lo haría el ingreso. Solo existe en {@code src/test} y con el perfil {@code test}
 * ({@code FirmaSesion} exige {@link TokenDeSesionHttp} fuera de él).
 * <ul>
 *   <li>Con MySQL real (dos usuarios), la sesión se abre por la ruta de identidad ({@code cc_sistema}) en una transacción
 *       propia, ya confirmada cuando la aprobación inserta su firma (trg_firma_operacion_nace la busca).</li>
 *   <li>Con H2 (un solo usuario), se abre en la transacción en curso: así también funciona dentro de una prueba
 *       {@code @Transactional} que se revierte.</li>
 * </ul>
 * Con MySQL real, si el principal no existe en la base (un {@code UsuarioAutenticado} inventado), no hay sesión: la firma
 * falla, como en producción. Con H2 (sin triggers), para que las pruebas de servicios que usan personas inventadas de los
 * escenarios ({@code EscenarioCobranza.PROMOTORIA}...) sigan firmando, se crea una cuenta SUPLENTE con ese id (inactiva,
 * sin roles: no aparece en ninguna consulta de personas activas). Los ids que genera la base para las cuentas reales
 * empiezan en {@value #PRIMER_ID_REAL}, así un suplente nunca choca con una cuenta real.
 */
@Component
@Primary
@Profile("test")
public class TokenDeSesionDePrueba implements TokenDeSesion {

	private final TokenDeSesionHttp http = new TokenDeSesionHttp();

	private final SesionUsuarioRepository sesiones;

	private final UsuarioRepository usuarios;

	private final FuenteDatosEnrutada fuente;

	private final PropiedadesSesion propiedades;

	private final TransactionTemplate propia;

	private final TransactionTemplate actual;

	private final Clock reloj;

	private final SecureRandom azar = new SecureRandom();

	/** Desde aquí numera la base (H2) las cuentas reales: los ids de las personas inventadas son menores. */
	static final long PRIMER_ID_REAL = 1_000_000L;

	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	/** Las sesiones abiertas para cada persona en esta JVM (se reusan mientras sigan abiertas en la base). */
	private final Map<Long, SesionAbierta> abiertas = new ConcurrentHashMap<>();

	public TokenDeSesionDePrueba(SesionUsuarioRepository sesiones, UsuarioRepository usuarios,
			javax.sql.DataSource fuente, PropiedadesSesion propiedades, PlatformTransactionManager transacciones,
			Clock reloj) {
		this.sesiones = sesiones;
		this.usuarios = usuarios;
		this.fuente = fuente instanceof FuenteDatosEnrutada enrutada ? enrutada : null;
		this.propiedades = propiedades;
		this.propia = new TransactionTemplate(transacciones);
		this.propia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.actual = new TransactionTemplate(transacciones);
		this.reloj = reloj;
		this.jdbc = new org.springframework.jdbc.core.JdbcTemplate(fuente);
		if (!enMySql()) {
			Long maximo = jdbc.queryForObject("SELECT MAX(id) FROM usuario", Long.class);
			if (maximo == null || maximo < PRIMER_ID_REAL) {
				jdbc.execute("ALTER TABLE usuario ALTER COLUMN id RESTART WITH " + Math.max(PRIMER_ID_REAL,
						maximo == null ? 0 : maximo + 1));
			}
		}
	}

	private boolean enMySql() {
		return fuente != null && fuente.separadas();
	}

	@Override
	public Optional<SesionAbierta> actual() {
		Optional<SesionAbierta> deLaPeticion = http.actual();
		if (deLaPeticion.isPresent()) {
			return deLaPeticion;
		}
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)) {
			return Optional.empty();
		}
		SesionAbierta conocida = abiertas.get(usuario.usuarioId());
		if (conocida != null && conocida.colegioId().equals(usuario.colegioId()) && sigueAbierta(conocida)) {
			return Optional.of(conocida);
		}
		Optional<SesionAbierta> nueva = abrir(usuario);
		nueva.ifPresent(s -> abiertas.put(usuario.usuarioId(), s));
		return nueva;
	}

	/** Olvida las sesiones conocidas (las pruebas que limpian la base). */
	public void olvidar() {
		abiertas.clear();
	}

	private boolean sigueAbierta(SesionAbierta s) {
		Boolean abierta = conTransaccion(() -> sesiones.findById(s.sesionId())
				.map(x -> x.vigente(LocalDateTime.now(reloj)) && x.getUsuarioId().equals(s.usuarioId())).orElse(false));
		return Boolean.TRUE.equals(abierta);
	}

	private Optional<SesionAbierta> abrir(UsuarioAutenticado usuario) {
		return conTransaccion(() -> {
			if (usuarios.findById(usuario.usuarioId()).filter(u -> u.isActivo() || !enMySql()).isEmpty()
					&& (enMySql() || !suplente(usuario))) {
				return Optional.<SesionAbierta>empty();
			}
			LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
			for (SesionUsuario s : sesiones.bloquearAbiertasDe(usuario.usuarioId())) {
				if (s.cerrar(MotivoCierreSesion.OTRA_SESION, ahora)) {
					sesiones.saveAndFlush(s);
				}
			}
			byte[] bytes = new byte[32];
			azar.nextBytes(bytes);
			String token = HexFormat.of().formatHex(bytes);
			SesionUsuario s = sesiones.saveAndFlush(SesionUsuario.abrir(usuario.usuarioId(), sha256(token), "127.0.0.1",
					ahora, ahora.plus(propiedades.vigenciaMaxima())));
			return Optional.of(new SesionAbierta(s.getId(), usuario.usuarioId(), usuario.colegioId(), token));
		});
	}

	/** H2: la cuenta suplente de una persona inventada (inactiva y sin roles), si su id está libre. */
	private boolean suplente(UsuarioAutenticado usuario) {
		java.util.List<Long> colegios = jdbc.queryForList("SELECT colegio_id FROM usuario WHERE id = ?", Long.class,
				usuario.usuarioId());
		if (!colegios.isEmpty()) {
			return colegios.getFirst().equals(usuario.colegioId());
		}
		if (jdbc.queryForObject("SELECT COUNT(*) FROM colegio WHERE id = ?", Long.class, usuario.colegioId()) == 0) {
			return false;
		}
		jdbc.update("INSERT INTO usuario (id, colegio_id, nombre_usuario, nombre_completo, clave_hash, activo, "
				+ "debe_cambiar_clave, intentos_fallidos, creado_en, creado_por, actualizado_en, version) VALUES (?, ?, ?, "
				+ "'Suplente de prueba', 'x', FALSE, FALSE, 0, CURRENT_TIMESTAMP, 'prueba', CURRENT_TIMESTAMP, 0)",
				usuario.usuarioId(), usuario.colegioId(), "prueba.firma." + usuario.usuarioId());
		return true;
	}

	/** MySQL real: por la ruta de identidad y confirmada aparte. H2: en la transacción en curso (o una nueva). */
	private <T> T conTransaccion(java.util.function.Supplier<T> operacion) {
		if (enMySql()) {
			return RutaConexion.identidad(() -> propia.execute(t -> operacion.get()));
		}
		return actual.execute(t -> operacion.get());
	}

	private static String sha256(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
