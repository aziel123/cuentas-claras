package pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.SesionUsuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.SesionUsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Las sesiones de la base (sprint 7, tanda 2; sección 3.4): la prueba de que la persona que aprueba está ahí.
 * <ol>
 *   <li><b>Al ingresar</b> se generan 32 bytes con {@code SecureRandom}; el secreto queda en la sesión HTTP y la base
 *       guarda solo su SHA-256, con la IP y el vencimiento (10 h aunque haya actividad, decisión 83). Un segundo ingreso
 *       cierra las anteriores ({@code OTRA_SESION}): una sola sesión por persona.</li>
 *   <li><b>Se cierra</b> al salir ({@code SALIO}), al expirar ({@code VENCIO}), al cambiar la clave, los roles o el
 *       contacto ({@code CUENTA_CAMBIADA}) y, al arrancar, todas las que quedaron abiertas ({@code REINICIO}).</li>
 * </ol>
 * Todo por la ruta de identidad: abrir y cerrar sesiones lo hace solo {@code cc_sistema} (1142 para {@code cc_app}).
 */
@Component
public class SesionesFirmadas implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(SesionesFirmadas.class);

	private static final int BYTES_SECRETO = 32;

	private final SesionUsuarioRepository sesiones;

	private final EjecucionIdentidad identidad;

	private final PropiedadesSesion propiedades;

	private final Clock reloj;

	private final SecureRandom azar = new SecureRandom();

	public SesionesFirmadas(SesionUsuarioRepository sesiones, EjecucionIdentidad identidad, PropiedadesSesion propiedades,
			Clock reloj) {
		this.sesiones = sesiones;
		this.identidad = identidad;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/** Abre la sesión de la base de quien acaba de ingresar (cierra las que tenía abiertas). */
	public SesionAbierta abrir(long colegioId, Long usuarioId, String ip) {
		Objects.requireNonNull(usuarioId, "usuarioId");
		return ContextoColegio.en(colegioId, () -> identidad.como(() -> {
			LocalDateTime ahora = ahora();
			cerrarAbiertas(usuarioId, MotivoCierreSesion.OTRA_SESION, ahora);
			byte[] bytes = new byte[BYTES_SECRETO];
			azar.nextBytes(bytes);
			String token = HexFormat.of().formatHex(bytes);
			SesionUsuario sesion = sesiones.saveAndFlush(SesionUsuario.abrir(usuarioId, hash(token), ip, ahora,
					ahora.plus(propiedades.vigenciaMaxima())));
			return new SesionAbierta(sesion.getId(), usuarioId, colegioId, token);
		}));
	}

	/** Cierra esa sesión de la base, si sigue abierta (al salir o cuando la sesión HTTP expira). */
	public void cerrar(SesionAbierta abierta, MotivoCierreSesion motivo) {
		Objects.requireNonNull(abierta, "abierta");
		ContextoColegio.en(abierta.colegioId(), () -> identidad.ejecutar(() -> sesiones.findById(abierta.sesionId())
				.filter(s -> s.getUsuarioId().equals(abierta.usuarioId()))
				.filter(s -> s.cerrar(motivo, ahora()))
				.ifPresent(sesiones::saveAndFlush)));
	}

	/**
	 * Cierra las sesiones abiertas de una persona dentro de una operación de identidad en curso (le cambiaron la clave, los
	 * roles o el contacto, o la desactivaron): sus secretos ya no firman.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int cerrarDe(Long usuarioId, MotivoCierreSesion motivo) {
		if (!RutaConexion.enIdentidad() && RutaConexion.actual() != RutaConexion.SISTEMA) {
			throw new IllegalStateException("Las sesiones se cierran por la ruta de identidad (cc_sistema)");
		}
		return cerrarAbiertas(usuarioId, motivo, ahora());
	}

	/** Si esa sesión sigue abierta y vigente (para las pruebas y la página de la cuenta). */
	@Transactional(readOnly = true)
	public boolean vigente(Long sesionId) {
		return sesiones.findById(sesionId).map(s -> s.vigente(ahora())).orElse(false);
	}

	/** Al arrancar: las sesiones HTTP anteriores ya no existen, así que ninguna sesión de la base sigue abierta. */
	@Override
	public void run(ApplicationArguments argumentos) {
		cerrarTodasAlArrancar();
	}

	public int cerrarTodasAlArrancar() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Se cierran al arrancar, fuera de toda transacción");
		}
		try {
			int cerradas = ContextoColegio.comoSistema(() -> identidad.como(() -> {
				LocalDateTime ahora = ahora();
				int n = 0;
				for (SesionUsuario s : sesiones.abiertas()) {
					if (s.cerrar(MotivoCierreSesion.REINICIO, ahora)) {
						sesiones.saveAndFlush(s);
						n++;
					}
				}
				return n;
			}));
			if (cerradas > 0) {
				LOG.info("Al arrancar se cerraron {} sesiones de la base que quedaron abiertas.", cerradas);
			}
			return cerradas;
		}
		catch (DataAccessException e) {
			// Fase 1 del despliegue (antes de 02-permisos-tablas.sql) cc_sistema solo lee: no hay nada que cerrar todavía.
			LOG.warn("No se pudieron cerrar las sesiones abiertas al arrancar ({}). Revisa los permisos de cc_sistema "
					+ "(docs/operacion/mysql-usuarios.md).", e.getClass().getSimpleName());
			return 0;
		}
	}

	private int cerrarAbiertas(Long usuarioId, MotivoCierreSesion motivo, LocalDateTime ahora) {
		int n = 0;
		for (SesionUsuario s : sesiones.bloquearAbiertasDe(usuarioId)) {
			if (s.cerrar(motivo, ahora)) {
				sesiones.saveAndFlush(s);
				n++;
			}
		}
		return n;
	}

	/** SHA-256 en hexadecimal (el mismo {@code SHA2(token, 256)} con que trg_firma_operacion_nace lo compara). */
	static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
