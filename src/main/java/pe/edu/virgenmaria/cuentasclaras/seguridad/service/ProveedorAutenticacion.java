package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.SelladorAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Autentica el formulario de ingreso. Por cada intento, en UNA transacción y con la fila del usuario
 * bloqueada ({@code SELECT ... FOR UPDATE}):
 * <ol>
 *   <li>si la cuenta está bloqueada, rechaza sin mirar la clave;</li>
 *   <li>si está desactivada, rechaza;</li>
 *   <li>si la clave no coincide, suma el intento (y bloquea al llegar al máximo);</li>
 *   <li>si la clave temporal venció, rechaza;</li>
 *   <li>si todo está bien, reinicia el contador.</li>
 * </ol>
 * Cada resultado se audita en la misma transacción. Así los intentos simultáneos sobre un usuario se
 * atienden de uno en uno: no se puede superar el máximo en paralelo y un ingreso correcto nunca
 * desbloquea una cuenta bloqueada.
 * <p>
 * Para un usuario inexistente se compara contra un hash señuelo (mismo tiempo de respuesta) y en la
 * bitácora se guarda solo un código HMAC del texto escrito, nunca el texto (podría ser una clave).
 */
@Component
public class ProveedorAutenticacion implements AuthenticationProvider {

	/** Prefijo del nombre registrado en la bitácora para quien no es usuario. */
	public static final String PREFIJO_DESCONOCIDO = "desconocido-";

	private enum Resultado { CORRECTO, BLOQUEADA, INACTIVA, CLAVE_INCORRECTA, CLAVE_TEMPORAL_VENCIDA }

	private record Intento(Resultado resultado, UsuarioAutenticado usuario) {
	}

	private final UsuarioRepository usuarios;

	private final ServicioDetallesUsuario detalles;

	private final PasswordEncoder codificador;

	private final AuditoriaService auditoria;

	private final SelladorAuditoria sellador;

	private final PropiedadesSeguridad propiedades;

	private final EjecucionIdentidad identidad;

	private final Clock reloj;

	private final String hashSenuelo;

	public ProveedorAutenticacion(UsuarioRepository usuarios, ServicioDetallesUsuario detalles,
			PasswordEncoder codificador, AuditoriaService auditoria, SelladorAuditoria sellador,
			PropiedadesSeguridad propiedades, EjecucionIdentidad identidad, Clock reloj) {
		this.usuarios = usuarios;
		this.detalles = detalles;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.sellador = sellador;
		this.propiedades = propiedades;
		this.identidad = identidad;
		this.reloj = reloj;
		this.hashSenuelo = codificador.encode("clave señuelo para igualar el tiempo de respuesta");
	}

	@Override
	public Authentication authenticate(Authentication solicitud) {
		String nombre = ServicioDetallesUsuario.normalizar(solicitud.getName());
		String clave = solicitud.getCredentials() == null ? "" : solicitud.getCredentials().toString();
		Optional<Long> colegio = detalles.colegioDe(nombre);
		if (colegio.isEmpty()) {
			codificador.matches(clave, hashSenuelo);
			auditoria.registrar(auditoria.actorPara(null, null, PREFIJO_DESCONOCIDO + sellador.codigoDeTexto(nombre), null),
					AccionAuditoria.INGRESO_FALLIDO, "usuario", null, null, null, "Usuario no registrado.");
			throw new BadCredentialsException("Usuario o clave incorrectos");
		}
		// Sprint 7, tanda 2: el contador de intentos, el bloqueo y el último ingreso se escriben por la ruta de identidad
		// (cc_sistema): cc_app no tiene UPDATE sobre usuario.
		Intento intento = ContextoColegio.en(colegio.get(), () -> identidad.como(() -> intentar(nombre, clave)));
		return switch (intento.resultado()) {
			case CORRECTO -> {
				UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken
						.authenticated(intento.usuario(), null, intento.usuario().getAuthorities());
				token.setDetails(solicitud.getDetails());
				yield token;
			}
			case BLOQUEADA -> throw new LockedException("Cuenta bloqueada");
			case INACTIVA -> throw new DisabledException("Cuenta desactivada");
			case CLAVE_TEMPORAL_VENCIDA -> throw new CredentialsExpiredException("Clave temporal vencida");
			case CLAVE_INCORRECTA -> throw new BadCredentialsException("Usuario o clave incorrectos");
		};
	}

	private Intento intentar(String nombre, String clave) {
		Usuario usuario = usuarios.bloquearPorNombreUsuario(nombre).orElseThrow();
		LocalDateTime ahora = LocalDateTime.now(reloj);
		Actor actor = actorDe(usuario);
		String id = usuario.getId().toString();
		if (usuario.estaBloqueado(ahora)) {
			auditoria.registrar(actor, AccionAuditoria.INGRESO_RECHAZADO_BLOQUEADA, "usuario", id, null, null,
					"La cuenta está bloqueada por intentos fallidos.");
			return new Intento(Resultado.BLOQUEADA, null);
		}
		if (!usuario.isActivo()) {
			auditoria.registrar(actor, AccionAuditoria.INGRESO_RECHAZADO_INACTIVA, "usuario", id, null, null,
					"La cuenta está desactivada.");
			return new Intento(Resultado.INACTIVA, null);
		}
		if (!codificador.matches(clave, usuario.getClaveHash())) {
			int numero = usuario.getIntentosFallidos() + 1;
			boolean bloqueada = usuario.registrarIngresoFallido(propiedades.intentosMaximos(),
					propiedades.duracionBloqueo(), ahora);
			auditoria.registrar(actor, AccionAuditoria.INGRESO_FALLIDO, "usuario", id, null, null,
					"Clave incorrecta. Intento " + numero + " de " + propiedades.intentosMaximos() + ".");
			if (bloqueada) {
				auditoria.registrar(actor, AccionAuditoria.CUENTA_BLOQUEADA, "usuario", id, null,
						"bloqueada hasta " + usuario.getBloqueadoHasta(),
						"Se bloqueó por " + propiedades.intentosMaximos() + " intentos fallidos seguidos.");
			}
			return new Intento(Resultado.CLAVE_INCORRECTA, null);
		}
		if (usuario.claveTemporalVencida(ahora)) {
			auditoria.registrar(actor, AccionAuditoria.INGRESO_RECHAZADO_CLAVE_VENCIDA, "usuario", id, null, null,
					"La clave temporal venció el " + usuario.getClaveTemporalHasta() + ".");
			return new Intento(Resultado.CLAVE_TEMPORAL_VENCIDA, null);
		}
		// S4-M2: el PRIMER ingreso de una cuenta queda señalado en la bitácora (con la IP del evento).
		boolean primero = usuario.getUltimoIngresoEn() == null;
		usuario.registrarIngresoExitoso(ahora);
		String detalle = usuario.isDebeCambiarClave() ? "Ingresó con clave temporal: debe cambiarla." : null;
		if (primero) {
			detalle = "PRIMER ingreso de esta cuenta" + (actor.ip() == null ? "" : ", desde la IP " + actor.ip()) + "."
					+ (detalle == null ? "" : " " + detalle);
		}
		auditoria.registrar(actor, AccionAuditoria.INGRESO_EXITOSO, "usuario", id, null, null, detalle);
		return new Intento(Resultado.CORRECTO, UsuarioAutenticado.de(usuario, ahora));
	}

	private Actor actorDe(Usuario usuario) {
		return auditoria.actorPara(usuario.getColegioId(), usuario.getId(), usuario.getNombreUsuario(),
				String.join(",", usuario.getRoles().stream().map(Enum::name).sorted().toList()));
	}

	@Override
	public boolean supports(Class<?> tipo) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
	}
}
