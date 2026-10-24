package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.RegistroSolicitudes;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContactoNormal;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Motivo;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.ContactoPersonalVista;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cambio del celular o el correo del PERSONAL (sprint 6, tanda 2; hallazgo 5, P6, decisión 78). Por ahí le llegan a
 * Promotoría la huella, el resumen diario y las alertas: si alguien pone su celular en la cuenta de la promotora, los
 * recibe él. Por eso:
 * <ul>
 *   <li>el titular lo pide para sí o Promotoría para otra persona; queda como solicitud CAMBIO_CONTACTO_PERSONAL que
 *       aprueba OTRA persona de Promotoría o Dirección (nunca quien la pidió ni el titular), resaltada en la bitácora;</li>
 *   <li>el contacto nuevo no puede ser el de un apoderado ni el de otra persona del personal (comparado normalizado);</li>
 *   <li>al aplicarse se avisa al contacto ANTERIOR ({@link ManejadorContactoPersonal});</li>
 *   <li>en MySQL, trg_usuario_contacto rechaza cualquier otro camino (un UPDATE sin su solicitud aprobada).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION','CAJA','DOCENTE')")
public class ServicioContactoPersonal {

	private static final Pattern CORREO = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}$");

	private final UsuarioRepository usuarios;

	private final RegistroSolicitudes solicitudes;

	private final ContactoPropio propio;

	private final AuditoriaService auditoria;

	private final CodigoPromotoria codigos;

	private final CorreoControlRegistrado correoControl;

	private final pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion firma;

	public ServicioContactoPersonal(UsuarioRepository usuarios, RegistroSolicitudes solicitudes, ContactoPropio propio,
			AuditoriaService auditoria, CodigoPromotoria codigos, CorreoControlRegistrado correoControl,
			pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion firma) {
		this.usuarios = usuarios;
		this.solicitudes = solicitudes;
		this.propio = propio;
		this.auditoria = auditoria;
		this.codigos = codigos;
		this.correoControl = correoControl;
		this.firma = firma;
	}

	/**
	 * Sprint 11, tanda 1c (A1c-1): la dueña de una cuenta de Promotoría o Dirección pide el código a su contacto ANTERIOR
	 * (el que hoy tiene registrado) para confirmar el cambio pedido. Sin esa confirmación (o la de dos operadores, si lo
	 * perdió), el contacto de una cuenta directiva no cambia: ni directo ni con la aprobación de otra persona.
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public CodigoPromotoria.Pedido pedirCodigoDelContactoAnterior(Long solicitudId) {
		SolicitudCambio solicitud = suyaPorConfirmar(solicitudId);
		return codigos.pedirAlContactoAnterior(new CodigoPromotoria.Accion(solicitud.huellaAccion(),
				"cambiar tu contacto (" + solicitud.getResumen() + ")"));
	}

	/** Sprint 11, tanda 1c (A1c-1): confirma el cambio de su contacto con el código que le llegó al anterior. */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	@Transactional
	public void confirmarDesdeContactoAnterior(Long solicitudId, CodigoPromotoria.Escrito escrito) {
		SolicitudCambio solicitud = suyaPorConfirmar(solicitudId);
		CodigoPromotoria.Valido valido = codigos.exigirDelContactoAnterior(escrito, solicitud.huellaAccion());
		firma.firmar(pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma.solicitudConfirmada(solicitudId));
		solicitudes.confirmarDesdeContactoAnterior(solicitud, valido.mensajeId(), valido.sha256());
		auditoria.registrar(AccionAuditoria.CONTACTO_CONFIRMADO, "solicitud_cambio", solicitudId.toString(), null,
				"confirmado desde el contacto anterior", "La dueña de la cuenta confirmó el cambio de su contacto con el "
						+ "código que le llegó a su contacto anterior. Ahora lo aprueba otra persona de Promotoría o Dirección.");
	}

	/** Si quien está en sesión puede confirmar este cambio de su propio contacto (A1c-1; para la pantalla). */
	@Transactional(readOnly = true)
	public boolean puedeConfirmar(Long solicitudId) {
		UsuarioAutenticado actor = actor();
		return solicitudes.pendienteDeContacto(solicitudId, actor.usuarioId())
				.filter(s -> !s.confirmadaDesdeContactoAnterior())
				.flatMap(s -> usuarios.findById(actor.usuarioId()))
				.map(u -> u.getRoles().contains(Rol.PROMOTOR) || u.getRoles().contains(Rol.DIRECTOR)).orElse(false);
	}

	/** Solo en dev y en la demo: el código del pedido, para mostrarlo en la pantalla. */
	public java.util.Optional<String> codigoDeDemostracion(CodigoPromotoria.Pedido pedido) {
		return pedido == null ? java.util.Optional.empty() : codigos.codigoDeDemostracion(pedido.mensajeId());
	}

	private SolicitudCambio suyaPorConfirmar(Long solicitudId) {
		UsuarioAutenticado actor = actor();
		return solicitudes.pendienteDeContacto(solicitudId, actor.usuarioId())
				.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
	}

	/** El contacto actual (enmascarado) de una persona del personal, para el formulario. */
	@Transactional(readOnly = true)
	public ContactoPersonalVista actual(Long usuarioId) {
		Usuario usuario = delPersonal(usuarioId);
		exigirTitularOPromotoria(usuario);
		return new ContactoPersonalVista(usuario.getId(), usuario.getNombreCompleto(),
				usuario.getTelefonoWhatsapp() == null ? null : Enmascarar.telefono(usuario.getTelefonoWhatsapp()),
				usuario.getCorreo() == null ? null : Enmascarar.correo(usuario.getCorreo()),
				!solicitudes.pendientesDe("usuario", usuario.getId()).isEmpty());
	}

	/**
	 * Pide el cambio. {@code telefono} y {@code correo} son los contactos NUEVOS que quedarán (vacío = sin ese canal);
	 * debe quedar al menos uno y algo debe cambiar.
	 *
	 * @return el id de la solicitud
	 */
	@Transactional
	public Long solicitar(Long usuarioId, String telefono, String correo, String motivo) {
		Usuario usuario = delPersonal(usuarioId);
		exigirTitularOPromotoria(usuario);
		String texto = Motivo.exigir(motivo);
		String nuevoTelefono = telefono(telefono);
		String nuevoCorreo = correo(correo);
		if (nuevoTelefono == null && nuevoCorreo == null) {
			throw new ReglaNegocioException("Escribe el celular (WhatsApp) o el correo: la cuenta debe quedar con uno.");
		}
		if (Objects.equals(nuevoTelefono, usuario.getTelefonoWhatsapp()) && Objects.equals(nuevoCorreo, usuario.getCorreo())) {
			throw new ReglaNegocioException("Ese ya es el contacto registrado: no hay nada que cambiar.");
		}
		propio.exigir(usuario, nuevoTelefono, "celular");
		propio.exigir(usuario, nuevoCorreo, "correo");
		// Sprint 11, tanda 1c (A1c-8): una Promotoría no pone en su cuenta el correo externo de control.
		if (nuevoCorreo != null && usuario.getRoles().contains(Rol.PROMOTOR) && correoControl.esElCorreoDeControl(nuevoCorreo)) {
			throw new ReglaNegocioException("Ese es el correo externo de control del colegio: no puede ser el de una persona "
					+ "de Promotoría (es el testigo independiente).");
		}
		Map<String, String> pedido = new LinkedHashMap<>();
		pedido.put("telefonoAnterior", vacioSiNulo(usuario.getTelefonoWhatsapp()));
		pedido.put("correoAnterior", vacioSiNulo(usuario.getCorreo()));
		pedido.put("telefono", vacioSiNulo(nuevoTelefono));
		pedido.put("correo", vacioSiNulo(nuevoCorreo));
		SolicitudCambio solicitud = solicitudes.crear(TipoSolicitud.CAMBIO_CONTACTO_PERSONAL, "usuario", usuario.getId(),
				"Contacto de " + usuario.getNombreCompleto() + " (" + usuario.getNombreUsuario() + "): "
						+ visible(usuario.getTelefonoWhatsapp(), usuario.getCorreo()) + " → "
						+ visible(nuevoTelefono, nuevoCorreo), pedido, texto);
		auditoria.registrar(AccionAuditoria.CONTACTO_PERSONAL_SOLICITADO, "usuario", usuario.getId().toString(),
				visible(usuario.getTelefonoWhatsapp(), usuario.getCorreo()), visible(nuevoTelefono, nuevoCorreo),
				"Pidió cambiar el contacto de " + usuario.getNombreUsuario() + " (solicitud " + solicitud.getId() + "). Lo "
						+ "aprueba otra persona de Promotoría o Dirección; al aprobarse se avisa al contacto anterior.");
		return solicitud.getId();
	}

	private Usuario delPersonal(Long usuarioId) {
		Usuario usuario = usuarios.findById(Objects.requireNonNull(usuarioId, "usuarioId"))
				.orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
		if (usuario.getApoderadoId() != null) {
			throw new RecursoNoEncontradoException("Usuario no encontrado");
		}
		if (!usuario.isActivo()) {
			throw new ReglaNegocioException("La cuenta está desactivada: no se cambia su contacto.");
		}
		return usuario;
	}

	/** El titular para sí; para otra persona, solo Promotoría (decisión 78). */
	private static void exigirTitularOPromotoria(Usuario usuario) {
		UsuarioAutenticado actor = actor();
		if (!actor.usuarioId().equals(usuario.getId()) && !actor.roles().contains(Rol.PROMOTOR)) {
			throw new AccessDeniedException("Solo Promotoría pide el cambio de contacto de otra persona");
		}
	}

	private static UsuarioAutenticado actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere un usuario en sesión");
	}

	static String telefono(String escrito) {
		if (escrito == null || escrito.isBlank()) {
			return null;
		}
		String normalizado = Telefono.normalizar(escrito);
		if (normalizado == null) {
			throw new ReglaNegocioException("Revisa el celular: escribe 9 dígitos que empiecen con 9.");
		}
		return normalizado;
	}

	static String correo(String escrito) {
		if (escrito == null || escrito.isBlank()) {
			return null;
		}
		String limpio = escrito.strip();
		if (limpio.length() > 150 || !CORREO.matcher(limpio).matches() || ContactoNormal.de(limpio).isEmpty()) {
			throw new ReglaNegocioException("Revisa el correo: no parece válido.");
		}
		return limpio;
	}

	static String visible(String telefono, String correo) {
		List<String> partes = new java.util.ArrayList<>();
		if (telefono != null && !telefono.isBlank()) {
			partes.add("WhatsApp " + Enmascarar.telefono(telefono));
		}
		if (correo != null && !correo.isBlank()) {
			partes.add("correo " + Enmascarar.correo(correo));
		}
		return partes.isEmpty() ? "sin contacto" : String.join(" y ", partes);
	}

	static String vacioSiNulo(String valor) {
		return valor == null ? "" : valor;
	}
}
