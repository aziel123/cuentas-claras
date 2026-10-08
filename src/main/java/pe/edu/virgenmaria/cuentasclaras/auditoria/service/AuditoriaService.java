package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.Actor;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EslabonCadena;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.EventoAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EslabonCadenaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.EventoAuditoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.PrincipalConColegio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

/**
 * Registra eventos en la bitácora inmutable.
 * <p>
 * Corre en la transacción de quien lo llama ({@code REQUIRED}): si la operación auditada falla,
 * el evento tampoco se guarda, y viceversa. Flujo: bloquea el eslabón de la cadena, asigna la
 * secuencia siguiente, sella el evento con el hash anterior, lo guarda y avanza el eslabón.
 * El bloqueo serializa los registros concurrentes, así la secuencia no se repite ni salta.
 * <p>
 * Nunca pases claves, hashes de claves ni datos sensibles en los valores o el detalle.
 */
@Service
@Transactional(propagation = Propagation.REQUIRED)
public class AuditoriaService {

	private static final String PREFIJO_ROL = "ROLE_";

	private final EventoAuditoriaRepository eventos;

	private final EslabonCadenaRepository cadena;

	private final SelladorAuditoria sellador;

	private final Clock reloj;

	public AuditoriaService(EventoAuditoriaRepository eventos, EslabonCadenaRepository cadena,
			SelladorAuditoria sellador, Clock reloj) {
		this.eventos = eventos;
		this.cadena = cadena;
		this.sellador = sellador;
		this.reloj = reloj;
	}

	/** Registra con el usuario autenticado, su colegio y la IP de la petición actual. */
	public EventoAuditoria registrar(AccionAuditoria accion, String entidad, String entidadId, String valorAnterior,
			String valorNuevo, String detalle) {
		return registrar(actorActual(), accion, entidad, entidadId, valorAnterior, valorNuevo, detalle);
	}

	/** Registra con un actor explícito (procesos sin usuario o eventos previos a la autenticación). */
	public EventoAuditoria registrar(Actor actor, AccionAuditoria accion, String entidad, String entidadId,
			String valorAnterior, String valorNuevo, String detalle) {
		EslabonCadena eslabon = cadena.bloquear();
		String hashAnterior = eslabon.getUltimoHash();
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		EventoAuditoria evento = EventoAuditoria.crear(eslabon.siguienteSecuencia(), actor, accion, entidad,
				entidadId, valorAnterior, valorNuevo, detalle, ahora, e -> sellador.sellar(hashAnterior, e));
		EventoAuditoria guardado = eventos.save(evento);
		eslabon.avanzar(guardado.getHash());
		return guardado;
	}

	/** Cuántos eventos de esa acción hubo en el colegio actual desde un momento (solo lectura; para alertas). */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public long contarDesde(AccionAuditoria accion, LocalDateTime desde) {
		Long colegio = ContextoColegio.actual();
		if (colegio == null || colegio <= 0) {
			return 0;
		}
		return eventos.countByColegioIdAndAccionAndOcurridoEnGreaterThanEqual(colegio, accion, desde);
	}

	/**
	 * Cuántos eventos de esa acción hizo la persona en sesión en el colegio actual desde un momento (solo lectura; tope
	 * diario de exportaciones del sprint 6). Sin persona en sesión, 0.
	 */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public long contarDesdeDelUsuarioActual(AccionAuditoria accion, LocalDateTime desde) {
		Long colegio = ContextoColegio.actual();
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (colegio == null || colegio <= 0 || autenticacion == null || !autenticacion.isAuthenticated()) {
			return 0;
		}
		return eventos.countByColegioIdAndAccionAndNombreUsuarioAndOcurridoEnGreaterThanEqual(colegio, accion,
				autenticacion.getName(), desde);
	}

	/**
	 * Actor explícito con la IP de la petición actual (si la hay). Para eventos en los que el usuario
	 * aún no está en el contexto de seguridad, como los intentos de ingreso o el cierre de sesión.
	 */
	@Transactional(propagation = Propagation.SUPPORTS)
	public Actor actorPara(Long colegioId, Long usuarioId, String nombreUsuario, String roles) {
		return new Actor(colegioId, usuarioId, nombreUsuario, roles == null || roles.isEmpty() ? null : roles,
				ipActual());
	}

	/** Actor de la petición actual: usuario autenticado, {@code anonimo} o {@code sistema}. */
	Actor actorActual() {
		Long colegio = ContextoColegio.actual();
		Long colegioId = colegio != null && colegio > 0 ? colegio : null;
		String ip = ipActual();
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !autenticacion.isAuthenticated()) {
			return new Actor(colegioId, null, Actor.SISTEMA, null, ip);
		}
		if (autenticacion instanceof AnonymousAuthenticationToken) {
			return new Actor(colegioId, null, Actor.ANONIMO, null, ip);
		}
		Long usuarioId = null;
		if (autenticacion.getPrincipal() instanceof PrincipalConColegio principal) {
			usuarioId = principal.usuarioId();
			if (principal.rolesParaAuditoria() != null && !principal.rolesParaAuditoria().isBlank()) {
				return new Actor(colegioId, usuarioId, autenticacion.getName(), principal.rolesParaAuditoria(), ip);
			}
		}
		String roles = autenticacion.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.filter(a -> a != null && a.startsWith(PREFIJO_ROL))
				.map(a -> a.substring(PREFIJO_ROL.length()))
				.sorted()
				.collect(Collectors.joining(","));
		return new Actor(colegioId, usuarioId, autenticacion.getName(), roles.isEmpty() ? null : roles, ip);
	}

	private static String ipActual() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
			return atributos.getRequest().getRemoteAddr();
		}
		return null;
	}
}
