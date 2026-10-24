package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 11, tanda 1c (decisión 216, A1c-1, A1c-5 y A1c-7): el código de 6 dígitos de una acción sensible que una
 * Promotoría aplica directo (y de la confirmación del cambio de contacto de una cuenta directiva desde su contacto
 * anterior). Reemplaza al código de la facultad del titular (que ahora es una acción más).
 * <ul>
 *   <li>«Enviar código» crea el mensaje al WhatsApp REGISTRADO de quien actúa (sin respaldo por correo, A1c-7), con la
 *       SHA-256 del código y la huella de la acción: el texto dice qué autoriza («Código 482913 para: Dar Promotoría a
 *       j.perez»).</li>
 *   <li>Vale 10 minutos y una vez (el UNIQUE de {@code codigo_mensaje_id} de cada tabla) y solo para esa acción.</li>
 *   <li>La aplicación lo compara aquí ({@link #exigir}); la base, otra vez, con {@code cc_codigo_promotoria} (lo escrito
 *       viaja como SHA-256).</li>
 * </ul>
 */
@Service
public class CodigoPromotoria {

	/** Cuánto vale el código (la base exige el mensaje de hace 10 minutos o menos). */
	public static final Duration VIGENCIA = Duration.ofMinutes(10);

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	/**
	 * Lo que autoriza un código: la huella de la acción (la misma SHA-256 que calcula la base) y su descripción para el
	 * mensaje (con los datos enmascarados).
	 */
	public record Accion(String huella, String texto) {

		public Accion {
			Objects.requireNonNull(huella, "huella");
			Objects.requireNonNull(texto, "texto");
			if (texto.length() > 180) {
				texto = texto.substring(0, 179) + "…";
			}
		}
	}

	/** El código pedido: el mensaje que lo lleva y a dónde salió (lo guarda la pantalla; no lleva el código). */
	public record Pedido(Long mensajeId, String destino, LocalDateTime venceEn) implements Serializable {
	}

	/** Lo que escribió la persona en la pantalla. */
	public record Escrito(Long mensajeId, String codigo) {

		public static Escrito de(Long mensajeId, String codigo) {
			return mensajeId == null || codigo == null || codigo.isBlank() ? null : new Escrito(mensajeId, codigo);
		}
	}

	/** El código comprobado: el mensaje y la SHA-256 de lo escrito (lo que se guarda y compara la base). */
	public record Valido(Long mensajeId, String sha256) {
	}

	private final UsuarioRepository usuarios;

	private final CodigosEnviados enviados;

	private final EjecucionIdentidad identidad;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final DerivadorSecreto derivador;

	private final Clock reloj;

	private final boolean demostracion;

	public CodigoPromotoria(UsuarioRepository usuarios, CodigosEnviados enviados, EjecucionIdentidad identidad,
			AuditoriaService auditoria, ApplicationEventPublisher eventos, DerivadorSecreto derivador, Clock reloj,
			Environment entorno) {
		this.usuarios = usuarios;
		this.enviados = enviados;
		this.identidad = identidad;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.derivador = derivador;
		this.reloj = reloj;
		List<String> perfiles = Arrays.asList(entorno.getActiveProfiles());
		// Solo en dev y en la demo (mensajería simulada, sin WhatsApp real): la pantalla muestra el código.
		this.demostracion = (perfiles.contains("dev") || perfiles.contains("demo")) && !perfiles.contains("prod")
				&& !perfiles.contains("piloto");
	}

	/** Una Promotoría activa pide el código de una acción sensible (le llega a su WhatsApp registrado). */
	public Pedido pedir(Accion accion) {
		UsuarioAutenticado actor = actor();
		return identidad.como(() -> {
			Usuario cuenta = usuarios.findById(actor.usuarioId()).filter(u -> u.isActivo() && u.getApoderadoId() == null
					&& u.getRoles().contains(Rol.PROMOTOR))
					.orElseThrow(() -> new AccessDeniedException("Solo una Promotoría activa pide este código"));
			return enviar(cuenta, false, accion, AccionAuditoria.CODIGO_PROMOTORIA_ENVIADO);
		});
	}

	/**
	 * A1c-1: la dueña de una cuenta directiva pide el código a su contacto ANTERIOR (el que hoy está registrado) para
	 * confirmar el cambio de su propio contacto.
	 */
	public Pedido pedirAlContactoAnterior(Accion accion) {
		UsuarioAutenticado actor = actor();
		return identidad.como(() -> {
			Usuario cuenta = usuarios.findById(actor.usuarioId()).filter(u -> u.isActivo() && u.getApoderadoId() == null
					&& (u.getRoles().contains(Rol.PROMOTOR) || u.getRoles().contains(Rol.DIRECTOR)))
					.orElseThrow(() -> new AccessDeniedException("Solo una cuenta de Promotoría o Dirección confirma así"));
			return enviar(cuenta, true, accion, AccionAuditoria.CODIGO_CONTACTO_ENVIADO);
		});
	}

	private Pedido enviar(Usuario cuenta, boolean contacto, Accion accion, AccionAuditoria evento) {
		if (cuenta.getTelefonoWhatsapp() == null || cuenta.getTelefonoWhatsapp().isBlank()) {
			throw new ReglaNegocioException("Tu cuenta no tiene un celular con WhatsApp registrado: el código llega solo por "
					+ "WhatsApp (nunca por correo en estas acciones).");
		}
		LocalDateTime ahora = LocalDateTime.now(reloj);
		CodigoSolicitado pedido = CodigoSolicitado.de(cuenta.getId(), contacto, (contacto ? "CODIGO_CONTACTO:"
				: "CODIGO_PROMOTORIA:") + UUID.randomUUID().toString().replace("-", "").substring(0, 16), accion.texto(),
				accion.huella(), ahora.plus(VIGENCIA).format(HORA));
		eventos.publishEvent(pedido);
		CodigoSolicitado.Resultado r = pedido.resultado();
		if (r.mensajeId() == null) {
			throw new IllegalStateException("La mensajería no creó el mensaje del código");
		}
		auditoria.registrar(evento, "usuario", cuenta.getId().toString(), null, r.destino(), "Código para: "
				+ accion.texto() + ". Salió a " + r.destino() + "; vale 10 minutos y una sola vez, solo para esa acción.");
		return new Pedido(r.mensajeId(), r.destino(), r.creadoEn().plus(VIGENCIA));
	}

	/** Solo en dev y en la demo (sin WhatsApp real): el código de un pedido, para mostrarlo en la pantalla. */
	public Optional<String> codigoDeDemostracion(Long mensajeId) {
		if (!demostracion || mensajeId == null) {
			return Optional.empty();
		}
		return enviados.buscar(mensajeId).map(c -> derivador.codigoPromotoria(ContextoColegio.actual(), c.clave()));
	}

	/**
	 * Comprueba lo escrito contra el código de ESTA acción, de quien está en sesión, de hace 10 minutos o menos. Devuelve
	 * el mensaje y la SHA-256 de lo escrito (lo que se guarda: la base lo vuelve a comprobar).
	 */
	public Valido exigir(Escrito escrito, String huellaAccion) {
		return exigir(escrito, huellaAccion, false);
	}

	/** A1c-1: lo mismo, para el código al contacto anterior. */
	public Valido exigirDelContactoAnterior(Escrito escrito, String huellaAccion) {
		return exigir(escrito, huellaAccion, true);
	}

	private Valido exigir(Escrito escrito, String huellaAccion, boolean contacto) {
		UsuarioAutenticado actor = actor();
		if (escrito == null) {
			throw new ReglaNegocioException("Esta acción pide tu código: pulsa «Enviar código», te llega por WhatsApp y vale "
					+ "10 minutos.");
		}
		CodigosEnviados.CodigoEnviado enviado = enviados.buscar(escrito.mensajeId())
				.filter(c -> c.contacto() == contacto && Objects.equals(c.usuarioId(), actor.usuarioId()))
				.orElseThrow(() -> new ReglaNegocioException("Ese código no es tuyo o no es de esta acción: pide uno nuevo."));
		if (!Objects.equals(enviado.huella(), huellaAccion)) {
			throw new ReglaNegocioException("Ese código es de otra acción: cada código sirve solo para lo que dice tu "
					+ "WhatsApp. Pide uno nuevo.");
		}
		if (enviado.creadoEn() == null || enviado.creadoEn().plus(VIGENCIA).isBefore(LocalDateTime.now(reloj))) {
			throw new ReglaNegocioException("Tu código venció (vale 10 minutos): pide uno nuevo.");
		}
		String limpio = escrito.codigo().replaceAll("\\s", "");
		String sha = Sha256.hex(limpio);
		if (!MessageDigest.isEqual(sha.getBytes(StandardCharsets.UTF_8),
				Objects.requireNonNullElse(enviado.codigoSha256(), "").getBytes(StandardCharsets.UTF_8))) {
			throw new ReglaNegocioException("El código no coincide con el que te llegó. Revísalo o pide uno nuevo.");
		}
		return new Valido(enviado.mensajeId(), sha);
	}

	private static UsuarioAutenticado actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere un usuario en sesión");
	}
}
