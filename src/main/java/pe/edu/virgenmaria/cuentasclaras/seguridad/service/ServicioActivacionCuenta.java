package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.EnlaceActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.EnlaceActivacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Activación de la cuenta con su enlace de un solo uso: del apoderado (correcciones del sprint 4, S4-M2) y, desde el
 * sprint 5, del personal. Es PÚBLICA (el titular todavía no tiene clave): el token del enlace es lo que autentica (32
 * bytes al azar; en la base solo su SHA-256) y, como confirmación, el apoderado escribe su número de documento y el
 * personal su nombre de usuario (en ambos casos, su nombre de usuario). Elige su clave (con la política
 * de siempre), el enlace queda usado y se audita resaltado con su IP; si es la misma IP de quien creó la cuenta,
 * Promotoría lo ve como alerta.
 */
@Service
public class ServicioActivacionCuenta {

	/**
	 * Lo que ve el titular antes de elegir su clave: su nombre, hasta cuándo sirve el enlace y si es del personal (confirma
	 * su nombre de usuario) o de un apoderado (confirma su documento).
	 */
	public record VistaActivacion(String nombreCompleto, LocalDateTime venceEn, boolean personal) {
	}

	private final EnlaceActivacionRepository enlaces;

	private final UsuarioRepository usuarios;

	private final PasswordEncoder codificador;

	private final AuditoriaService auditoria;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final EjecucionIdentidad identidad;

	/** Sprint 7, tanda 3 (Ley 29733): la versión vigente del aviso de privacidad que acepta la familia. */
	private final String versionAviso;

	public ServicioActivacionCuenta(EnlaceActivacionRepository enlaces, UsuarioRepository usuarios,
			PasswordEncoder codificador, AuditoriaService auditoria, PlatformTransactionManager transacciones, Clock reloj,
			EjecucionIdentidad identidad,
			@org.springframework.beans.factory.annotation.Value("${cuentasclaras.privacidad.version-aviso:2027-01}")
			String versionAviso) {
		this.versionAviso = versionAviso;
		this.identidad = identidad;
		this.enlaces = enlaces;
		this.usuarios = usuarios;
		this.codificador = codificador;
		this.auditoria = auditoria;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	/** El enlace, si todavía sirve (vacío si no existe, ya se usó, venció o se reemplazó). */
	public Optional<VistaActivacion> vista(long colegioId, String token) {
		if (!tokenValido(token) || colegioId <= 0) {
			return Optional.empty();
		}
		return ContextoColegio.en(colegioId, () -> Optional.ofNullable(transaccion.execute(t -> {
			LocalDateTime ahora = ahora();
			return enlaces.bloquearPorHash(EnlacesActivacion.hash(token)).filter(e -> e.vigente(ahora))
					.flatMap(e -> usuarios.findById(e.getUsuarioId()).filter(Usuario::isActivo)
							.map(u -> new VistaActivacion(u.getNombreCompleto(), e.getVenceEn(),
									e.getProposito() == PropositoEnlace.PERSONAL)))
					.orElse(null);
		})));
	}

	/**
	 * Activa la cuenta: el apoderado confirma su documento y elige su clave. Sprint 7, tanda 3 (Ley 29733): la familia acepta
	 * el aviso de privacidad vigente ({@code aceptaPrivacidad}); queda en la bitácora con su versión. El personal no lo
	 * acepta aquí (sus datos son los de su relación laboral).
	 */
	public void activar(long colegioId, String token, String documento, String clave, String confirmacion,
			boolean aceptaPrivacidad) {
		if (!tokenValido(token) || colegioId <= 0) {
			throw new ReglaNegocioException(ENLACE_NO_SIRVE);
		}
		// Sprint 7, tanda 2: la clave se escribe por la ruta de identidad (cc_sistema).
		ContextoColegio.en(colegioId, () -> identidad.ejecutar(() -> {
			LocalDateTime ahora = ahora();
			EnlaceActivacion enlace = enlaces.bloquearPorHash(EnlacesActivacion.hash(token)).filter(e -> e.vigente(ahora))
					.orElseThrow(() -> new ReglaNegocioException(ENLACE_NO_SIRVE));
			Usuario usuario = usuarios.bloquearPorId(enlace.getUsuarioId()).filter(Usuario::isActivo)
					.orElseThrow(() -> new ReglaNegocioException(ENLACE_NO_SIRVE));
			String escrito = documento == null ? "" : documento.strip().toLowerCase(java.util.Locale.ROOT);
			if (!escrito.equals(usuario.getNombreUsuario())) {
				throw new ReglaNegocioException(enlace.getProposito() == PropositoEnlace.PERSONAL
						? "El nombre de usuario no corresponde a esta cuenta."
						: "El número de documento no corresponde a esta cuenta.");
			}
			if (clave == null || !clave.equals(confirmacion)) {
				throw new ReglaNegocioException("La clave y su confirmación no coinciden.");
			}
			boolean personalDelColegio = enlace.getProposito() == PropositoEnlace.PERSONAL;
			if (!personalDelColegio && !aceptaPrivacidad) {
				throw new ReglaNegocioException("Para activar tu cuenta, lee el aviso de privacidad y marca que lo aceptas.");
			}
			PoliticaClaves.validar(clave, usuario.getNombreUsuario());
			usuario.cambiarClave(codificador.encode(clave), ahora, false);
			usuario.desbloquear();
			usuarios.save(usuario);
			enlace.usar(ahora, EnlacesActivacion.ipActual());
			enlaces.save(enlace);
			boolean mismaIp = enlace.usadoDesdeLaIpDeQuienLoCreo();
			boolean personal = enlace.getProposito() == PropositoEnlace.PERSONAL;
			auditoria.registrar(auditoria.actorPara(colegioId, usuario.getId(), usuario.getNombreUsuario(),
					usuario.getRoles().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","))),
					personal ? AccionAuditoria.ACCESO_PERSONAL_ACTIVADO : AccionAuditoria.ACCESO_APODERADO_ACTIVADO,
					"usuario", usuario.getId().toString(), null, "activada",
					usuario.getNombreCompleto() + " activó su cuenta con su enlace y eligió su clave, desde la IP "
							+ enlace.getUsadoIp() + ". El enlace lo creó " + enlace.getCreadoPor() + " desde la IP "
							+ enlace.getCreadoIp() + "." + (mismaIp ? " ATENCIÓN: es la MISMA IP de quien creó la cuenta: "
									+ "confirma con el apoderado que fue él." : ""));
			if (!personal) {
				auditoria.registrar(auditoria.actorPara(colegioId, usuario.getId(), usuario.getNombreUsuario(), "APODERADO"),
						AccionAuditoria.PRIVACIDAD_ACEPTADA, "usuario", usuario.getId().toString(), null, versionAviso,
						usuario.getNombreCompleto() + " aceptó el aviso de privacidad, versión " + versionAviso
								+ ", al activar su cuenta.");
			}
		}));
	}

	private static final String ENLACE_NO_SIRVE = "Este enlace ya no sirve (se usó, venció o se reemplazó). Pide al colegio "
			+ "uno nuevo: Promotoría puede restablecer tu acceso.";

	private static boolean tokenValido(String token) {
		return token != null && token.matches("[A-Za-z0-9_-]{43}");
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}
}
