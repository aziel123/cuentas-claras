package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cuenta los intentos de ingreso y los audita, escuchando los eventos que publica Spring Security.
 * <ul>
 *   <li>Éxito: reinicia el contador y audita {@code INGRESO_EXITOSO}.</li>
 *   <li>Clave incorrecta: suma un intento con bloqueo pesimista de la fila (sin carreras entre intentos
 *       simultáneos) y audita {@code INGRESO_FALLIDO}; al llegar al máximo bloquea la cuenta y audita
 *       {@code CUENTA_BLOQUEADA}. Si el usuario no existe, audita sin colegio.</li>
 *   <li>Cuenta bloqueada o desactivada: audita el rechazo.</li>
 * </ul>
 * Cada operación fija el colegio del usuario con {@link ContextoColegio#en} y abre su transacción dentro.
 */
@Service
public class ServicioIntentosIngreso {

	private final UsuarioRepository usuarios;

	private final ServicioDetallesUsuario detalles;

	private final AuditoriaService auditoria;

	private final PropiedadesSeguridad propiedades;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public ServicioIntentosIngreso(UsuarioRepository usuarios, ServicioDetallesUsuario detalles,
			AuditoriaService auditoria, PropiedadesSeguridad propiedades, PlatformTransactionManager transacciones,
			Clock reloj) {
		this.usuarios = usuarios;
		this.detalles = detalles;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	@EventListener
	public void alIngresar(AuthenticationSuccessEvent evento) {
		if (!(evento.getAuthentication().getPrincipal() instanceof UsuarioAutenticado usuario)) {
			return;
		}
		ContextoColegio.en(usuario.colegioId(), () -> transaccion.executeWithoutResult(estado -> {
			usuarios.bloquearPorNombreUsuario(usuario.getUsername())
					.ifPresent(u -> u.registrarIngresoExitoso(ahora()));
			auditoria.registrar(actorDe(usuario), AccionAuditoria.INGRESO_EXITOSO, "usuario", id(usuario), null, null,
					usuario.debeCambiarClave() ? "Ingresó con clave temporal: debe cambiarla." : null);
		}));
	}

	@EventListener
	public void alFallarLaClave(AuthenticationFailureBadCredentialsEvent evento) {
		String nombre = nombreIntentado(evento.getAuthentication());
		Optional<Long> colegio = detalles.colegioDe(nombre);
		if (colegio.isEmpty()) {
			auditoria.registrar(auditoria.actorPara(null, null, nombreParaAuditoria(nombre), null),
					AccionAuditoria.INGRESO_FALLIDO, "usuario", null, null, null, "Usuario no registrado.");
			return;
		}
		ContextoColegio.en(colegio.get(), () -> transaccion.executeWithoutResult(estado -> {
			Usuario usuario = usuarios.bloquearPorNombreUsuario(nombre).orElseThrow();
			int intento = usuario.getIntentosFallidos() + 1;
			boolean bloqueada = usuario.registrarIngresoFallido(propiedades.intentosMaximos(),
					propiedades.duracionBloqueo(), ahora());
			Actor actor = actorDe(usuario);
			auditoria.registrar(actor, AccionAuditoria.INGRESO_FALLIDO, "usuario", usuario.getId().toString(), null,
					null, "Clave incorrecta. Intento " + intento + " de " + propiedades.intentosMaximos() + ".");
			if (bloqueada) {
				auditoria.registrar(actor, AccionAuditoria.CUENTA_BLOQUEADA, "usuario", usuario.getId().toString(),
						null, "bloqueada hasta " + usuario.getBloqueadoHasta(),
						"Se bloqueó por " + propiedades.intentosMaximos() + " intentos fallidos seguidos.");
			}
		}));
	}

	@EventListener
	public void alRechazarCuentaBloqueada(AuthenticationFailureLockedEvent evento) {
		auditarRechazo(evento.getAuthentication(), AccionAuditoria.INGRESO_RECHAZADO_BLOQUEADA,
				"La cuenta está bloqueada por intentos fallidos.");
	}

	@EventListener
	public void alRechazarCuentaInactiva(AuthenticationFailureDisabledEvent evento) {
		auditarRechazo(evento.getAuthentication(), AccionAuditoria.INGRESO_RECHAZADO_INACTIVA,
				"La cuenta está desactivada.");
	}

	private void auditarRechazo(Authentication autenticacion, AccionAuditoria accion, String detalle) {
		String nombre = nombreIntentado(autenticacion);
		Optional<Long> colegio = detalles.colegioDe(nombre);
		if (colegio.isEmpty()) {
			auditoria.registrar(auditoria.actorPara(null, null, nombreParaAuditoria(nombre), null), accion, "usuario",
					null, null, null, detalle);
			return;
		}
		ContextoColegio.en(colegio.get(), () -> transaccion.executeWithoutResult(estado -> {
			Usuario usuario = usuarios.findByNombreUsuario(nombre).orElseThrow();
			auditoria.registrar(actorDe(usuario), accion, "usuario", usuario.getId().toString(), null, null, detalle);
		}));
	}

	private Actor actorDe(UsuarioAutenticado usuario) {
		return auditoria.actorPara(usuario.colegioId(), usuario.usuarioId(), usuario.getUsername(),
				usuario.rolesComoTexto());
	}

	private Actor actorDe(Usuario usuario) {
		return auditoria.actorPara(usuario.getColegioId(), usuario.getId(), usuario.getNombreUsuario(),
				String.join(",", usuario.getRoles().stream().map(Enum::name).sorted().toList()));
	}

	private static String id(UsuarioAutenticado usuario) {
		return usuario.usuarioId().toString();
	}

	private static String nombreIntentado(Authentication autenticacion) {
		return ServicioDetallesUsuario.normalizar(autenticacion.getName());
	}

	/** Lo que escribió un visitante como usuario (nunca la clave); vacío se registra como {@code anonimo}. */
	private static String nombreParaAuditoria(String nombre) {
		return nombre.isEmpty() ? Actor.ANONIMO : nombre;
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj);
	}
}
