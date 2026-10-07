package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.TextoSeguro;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioCreado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioDetalle;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioResumen;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gestión de usuarios del colegio (Promotoría y Dirección).
 * <ul>
 *   <li>Jerarquía: Dirección no modifica usuarios de Promotoría ni de Dirección. Tampoco crea usuarios de
 *       Administración o Caja, ni les cambia los roles o la clave, ni asigna esos roles (auditoría A5). Intentarlo es
 *       una manipulación del formulario: responde 403.</li>
 *   <li>Nadie se modifica a sí mismo.</li>
 *   <li>Siempre queda al menos un usuario de Promotoría activo.</li>
 *   <li>Segregación de roles ({@link pe.edu.virgenmaria.cuentasclaras.seguridad.model.ReglasSegregacion}).</li>
 *   <li>Cada cambio exige motivo, cierra las sesiones del afectado si cambia su acceso y se audita
 *       con el valor anterior y el nuevo (nunca con claves).</li>
 * </ul>
 * Las consultas las filtra {@code @TenantId}: un usuario de otro colegio "no existe" (404).
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class ServicioUsuarios {

	private static final Set<Rol> ROLES_DIRECTIVOS = EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR);

	/**
	 * Roles que solo Promotoría asigna, y cuentas que solo Promotoría crea o a las que les cambia los roles o la clave
	 * (auditoría A5: Dirección no puede fabricarse una segunda cuenta de Administración o Caja para aprobar sus propios
	 * cambios). Dirección sí puede desactivar, reactivar o desbloquear a Administración y Caja.
	 */
	private static final Set<Rol> ROLES_DE_PROMOTORIA = EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR, Rol.ADMINISTRACION,
			Rol.CAJA);

	private final UsuarioRepository usuarios;

	private final ServicioDetallesUsuario detalles;

	private final PasswordEncoder codificador;

	private final GeneradorClaveTemporal generador;

	private final AuditoriaService auditoria;

	private final SesionesUsuario sesiones;

	private final TransactionTemplate transaccion;

	private final PropiedadesSeguridad propiedades;

	private final Clock reloj;

	private final EnlacesActivacion enlaces;

	private final ApplicationEventPublisher eventos;

	public ServicioUsuarios(UsuarioRepository usuarios, ServicioDetallesUsuario detalles, PasswordEncoder codificador,
			GeneradorClaveTemporal generador, AuditoriaService auditoria, SesionesUsuario sesiones,
			PropiedadesSeguridad propiedades, PlatformTransactionManager transacciones, Clock reloj,
			EnlacesActivacion enlaces, ApplicationEventPublisher eventos) {
		this.enlaces = enlaces;
		this.eventos = eventos;
		this.propiedades = propiedades;
		this.usuarios = usuarios;
		this.detalles = detalles;
		this.codificador = codificador;
		this.generador = generador;
		this.auditoria = auditoria;
		this.sesiones = sesiones;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public List<UsuarioResumen> listar() {
		LocalDateTime ahora = ahora();
		return usuarios.findAllByOrderByNombreCompletoAsc().stream()
				.map(u -> new UsuarioResumen(u.getId(), u.getNombreUsuario(), u.getNombreCompleto(),
						rolesTexto(u.getRoles()), estado(u, ahora)))
				.toList();
	}

	@Transactional(readOnly = true)
	public UsuarioDetalle obtener(Long id) {
		Usuario u = buscar(id);
		UsuarioAutenticado actor = actor();
		LocalDateTime ahora = ahora();
		boolean esUnoMismo = actor.usuarioId().equals(u.getId());
		return new UsuarioDetalle(u.getId(), u.getNombreUsuario(), u.getNombreCompleto(), u.getCorreo(), u.getRoles(),
				rolesTexto(u.getRoles()), estado(u, ahora), u.isActivo(), u.estaBloqueado(ahora), u.getBloqueadoHasta(),
				u.isDebeCambiarClave(), u.getUltimoIngresoEn(), u.getCreadoEn(), u.getCreadoPor(), u.getDesactivadoEn(),
				u.getDesactivadoPor(), esUnoMismo, !esUnoMismo && puedeTocar(actor, u.getRoles()),
				!esUnoMismo && puedeDarAcceso(actor, u.getRoles()));
	}

	/** Roles que quien está en sesión puede asignar. */
	public Set<Rol> rolesAsignables() {
		Set<Rol> roles = EnumSet.allOf(Rol.class);
		// Sprint 4: la cuenta del apoderado se crea desde su ficha (enlazada a su familia), no desde aquí.
		roles.remove(Rol.APODERADO);
		if (!esPromotor(actor())) {
			roles.removeAll(ROLES_DE_PROMOTORIA);
		}
		return roles;
	}

	/**
	 * Crea el usuario SIN clave conocida por nadie (una al azar, ya vencida) y le envía DIRECTO a su celular (WhatsApp) o
	 * a su correo un enlace de un solo uso para que elija su clave (sprint 5, A2). Quien lo crea solo ve a dónde se envió.
	 * No es {@code @Transactional}: el nombre se busca antes en toda la plataforma (fuera de transacción).
	 */
	public UsuarioCreado crear(CrearUsuarioRequest solicitud) {
		UsuarioAutenticado actor = actor();
		Set<Rol> roles = rolesValidos(solicitud.roles());
		exigirPuedeAsignar(actor, roles);
		String nombreUsuario = Usuario.normalizarNombreUsuario(solicitud.nombreUsuario());
		if (!Usuario.esNombreUsuarioValido(nombreUsuario)) {
			throw new ReglaNegocioException("El usuario debe tener de 3 a 60 caracteres: letras minúsculas sin tildes, "
					+ "números, punto, guion o guion bajo. Por ejemplo: lucia.ramos");
		}
		if (detalles.colegioDe(nombreUsuario).isPresent()) {
			throw nombreRepetido(nombreUsuario);
		}
		String telefono = telefono(solicitud.telefonoWhatsapp());
		String correo = solicitud.correo() == null || solicitud.correo().isBlank() ? null : solicitud.correo().strip();
		if (telefono == null && correo == null) {
			throw new ReglaNegocioException("Escribe el celular (WhatsApp) o el correo de la persona: ahí le llega el "
					+ "enlace para activar su cuenta. Nadie más ve ese enlace.");
		}
		Usuario usuario;
		try {
			usuario = transaccion.execute(estado -> {
				exigirNoEsMiContacto(actor, telefono, correo);
				LocalDateTime ahora = ahora();
				// Una clave al azar que nadie conoce y que ya venció: la cuenta solo se activa con el enlace.
				Usuario nuevo = Usuario.nuevo(nombreUsuario, solicitud.nombreCompleto(), correo,
						codificador.encode(generador.generar()), roles);
				nuevo.asignarTelefonoWhatsapp(telefono);
				nuevo.vencerClaveTemporalEn(ahora);
				usuarios.saveAndFlush(nuevo);
				auditoria.registrar(AccionAuditoria.USUARIO_CREADO, "usuario", nuevo.getId().toString(), null,
						"roles=" + roles(nuevo.getRoles()) + "; activo; enlace de activación al titular",
						"Usuario " + nuevo.getNombreUsuario() + " (" + nuevo.getNombreCompleto() + "). Su enlace de un solo "
								+ "uso va a " + destinoEnmascarado(nuevo) + ": nadie más lo ve.");
				eventos.publishEvent(new EnvioEnlaceSolicitado(nuevo.getId(), PropositoEnlace.PERSONAL, null));
				return nuevo;
			});
		}
		catch (DataIntegrityViolationException e) {
			// Otra persona creó el mismo nombre al mismo tiempo: lo detiene la restricción única de la base.
			throw nombreRepetido(nombreUsuario);
		}
		return enviado(usuario);
	}

	private static ReglaNegocioException nombreRepetido(String nombreUsuario) {
		return new ReglaNegocioException("Ya existe un usuario «" + nombreUsuario + "» en la plataforma. "
				+ "Elige otro, por ejemplo agregando la inicial del segundo apellido.");
	}

	/** Al menos un rol válido; si no, un mensaje claro (nunca un error 500). */
	private static Set<Rol> rolesValidos(Set<Rol> roles) {
		if (roles == null || roles.isEmpty() || roles.stream().anyMatch(java.util.Objects::isNull)) {
			throw new ReglaNegocioException("Elige al menos un rol.");
		}
		return EnumSet.copyOf(roles);
	}

	@Transactional
	public void cambiarRoles(Long id, CambiarRolesRequest solicitud) {
		UsuarioAutenticado actor = actor();
		Usuario usuario = buscar(id);
		exigirNoEsUnoMismo(actor, usuario, "No puedes cambiar tus propios roles.");
		Set<Rol> nuevos = rolesValidos(solicitud.roles());
		if (!nuevos.contains(Rol.PROMOTOR)) {
			exigirNoEsElUltimoPromotor(usuario, "No puedes quitarle Promotoría al último usuario de Promotoría activo.");
		}
		exigirPuedeTocar(actor, usuario);
		exigirPuedeAsignar(actor, usuario.getRoles());
		exigirPuedeAsignar(actor, nuevos);
		String motivo = motivo(solicitud.motivo());
		String anteriores = roles(usuario.getRoles());
		usuario.cambiarRoles(nuevos);
		auditoria.registrar(AccionAuditoria.ROLES_CAMBIADOS, "usuario", id.toString(), anteriores, roles(nuevos),
				detalle(usuario, motivo));
		sesiones.expirar(id);
	}

	@Transactional
	public void desactivar(Long id, String motivo) {
		UsuarioAutenticado actor = actor();
		Usuario usuario = buscar(id);
		exigirNoEsUnoMismo(actor, usuario, "No puedes desactivar tu propio usuario.");
		exigirNoEsElUltimoPromotor(usuario, "No puedes desactivar al último usuario de Promotoría activo.");
		exigirPuedeTocar(actor, usuario);
		String texto = motivo(motivo);
		usuario.desactivar(actor.getUsername(), ahora());
		auditoria.registrar(AccionAuditoria.USUARIO_DESACTIVADO, "usuario", id.toString(), "activo", "inactivo",
				detalle(usuario, texto));
		sesiones.expirar(id);
	}

	@Transactional
	public void reactivar(Long id, String motivo) {
		UsuarioAutenticado actor = actor();
		Usuario usuario = buscar(id);
		exigirNoEsUnoMismo(actor, usuario, "No puedes reactivar tu propio usuario.");
		exigirPuedeTocar(actor, usuario);
		String texto = motivo(motivo);
		usuario.reactivar();
		auditoria.registrar(AccionAuditoria.USUARIO_REACTIVADO, "usuario", id.toString(), "inactivo", "activo",
				detalle(usuario, texto));
	}

	/**
	 * Restablece el acceso: anula los enlaces anteriores, deja una clave al azar ya vencida (nadie la conoce), desbloquea
	 * la cuenta, cierra sus sesiones y envía un enlace nuevo DIRECTO al titular (sprint 5). No devuelve ninguna clave.
	 */
	@Transactional
	public UsuarioCreado restablecerClave(Long id, String motivo) {
		UsuarioAutenticado actor = actor();
		Usuario usuario = buscar(id);
		exigirNoEsUnoMismo(actor, usuario, "Para cambiar tu propia clave usa «Cambiar clave».");
		exigirPuedeTocar(actor, usuario);
		exigirPuedeAsignar(actor, usuario.getRoles());
		String texto = motivo(motivo);
		if (usuario.getTelefonoWhatsapp() == null && usuario.getCorreo() == null) {
			throw new ReglaNegocioException("La cuenta de " + usuario.getNombreUsuario() + " no tiene celular ni correo: "
					+ "no hay dónde enviarle su enlace. Registra primero su contacto.");
		}
		exigirNoEsMiContacto(actor, usuario.getTelefonoWhatsapp(), usuario.getCorreo());
		LocalDateTime ahora = ahora();
		enlaces.anularVigentes(usuario.getId());
		usuario.restablecerClave(codificador.encode(generador.generar()), ahora, ahora, actor.getUsername());
		usuarios.saveAndFlush(usuario);
		auditoria.registrar(AccionAuditoria.CLAVE_RESTABLECIDA, "usuario", id.toString(), null,
				"enlace nuevo de un solo uso al titular", detalle(usuario, texto) + ". El enlace va a "
						+ destinoEnmascarado(usuario) + "; los anteriores ya no sirven.");
		eventos.publishEvent(new EnvioEnlaceSolicitado(usuario.getId(), PropositoEnlace.PERSONAL, null));
		sesiones.expirar(id);
		return enviado(usuario);
	}

	/** El enlace no va a un contacto de quien lo pide (G8; en MySQL también lo impide trg_mensaje_nace). */
	private void exigirNoEsMiContacto(UsuarioAutenticado actor, String telefono, String correo) {
		usuarios.findById(actor.usuarioId()).ifPresent(yo -> {
			if (yo.tieneContacto(telefono) || yo.tieneContacto(correo)) {
				throw new ReglaNegocioException("Ese celular o correo es tuyo: el enlace debe llegar solo a su titular.");
			}
		});
	}

	private static String telefono(String escrito) {
		if (escrito == null || escrito.isBlank()) {
			return null;
		}
		String normalizado = Telefono.normalizar(escrito);
		if (normalizado == null) {
			throw new ReglaNegocioException("Revisa el celular: escribe 9 dígitos que empiecen con 9.");
		}
		return normalizado;
	}

	/** El canal preferido: WhatsApp si tiene celular; si no, correo (lo mismo que hace la mensajería). */
	static String canal(Usuario usuario) {
		return usuario.getTelefonoWhatsapp() != null ? "WhatsApp" : "correo";
	}

	static String destinoEnmascarado(Usuario usuario) {
		return usuario.getTelefonoWhatsapp() != null ? "WhatsApp " + Enmascarar.telefono(usuario.getTelefonoWhatsapp())
				: "correo " + Enmascarar.correo(usuario.getCorreo());
	}

	private UsuarioCreado enviado(Usuario usuario) {
		return new UsuarioCreado(usuario.getId(), usuario.getNombreUsuario(), usuario.getNombreCompleto(), canal(usuario),
				usuario.getTelefonoWhatsapp() != null ? Enmascarar.telefono(usuario.getTelefonoWhatsapp())
						: Enmascarar.correo(usuario.getCorreo()),
				ahora().plus(propiedades.vigenciaClaveTemporal()));
	}

	@Transactional
	public void desbloquear(Long id, String motivo) {
		UsuarioAutenticado actor = actor();
		Usuario usuario = buscar(id);
		exigirNoEsUnoMismo(actor, usuario, "No puedes desbloquear tu propio usuario.");
		exigirPuedeTocar(actor, usuario);
		if (!usuario.estaBloqueado(ahora())) {
			throw new ReglaNegocioException("La cuenta de " + usuario.getNombreUsuario() + " no está bloqueada.");
		}
		String texto = motivo(motivo);
		String anterior = "bloqueada hasta " + usuario.getBloqueadoHasta();
		usuario.desbloquear();
		auditoria.registrar(AccionAuditoria.CUENTA_DESBLOQUEADA, "usuario", id.toString(), anterior, "desbloqueada",
				detalle(usuario, texto));
	}

	private Usuario buscar(Long id) {
		return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
	}

	private static UsuarioAutenticado actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere un usuario en sesión");
	}

	private static boolean esPromotor(UsuarioAutenticado actor) {
		return actor.roles().contains(Rol.PROMOTOR);
	}

	private static boolean puedeTocar(UsuarioAutenticado actor, Collection<Rol> rolesDelObjetivo) {
		return esPromotor(actor) || rolesDelObjetivo.stream().noneMatch(ROLES_DIRECTIVOS::contains);
	}

	/** Crear, cambiar roles o restablecer la clave: da acceso a la cuenta (auditoría A5). */
	private static boolean puedeDarAcceso(UsuarioAutenticado actor, Collection<Rol> roles) {
		return esPromotor(actor) || roles.stream().noneMatch(ROLES_DE_PROMOTORIA::contains);
	}

	private static void exigirPuedeTocar(UsuarioAutenticado actor, Usuario objetivo) {
		if (!puedeTocar(actor, objetivo.getRoles())) {
			throw new AccessDeniedException("Dirección no puede modificar usuarios de Promotoría ni de Dirección");
		}
	}

	private static void exigirPuedeAsignar(UsuarioAutenticado actor, Collection<Rol> roles) {
		if (roles != null && !puedeDarAcceso(actor, roles)) {
			throw new AccessDeniedException("Dirección no puede asignar los roles de Promotoría, Dirección, "
					+ "Administración ni Caja");
		}
	}

	private static void exigirNoEsUnoMismo(UsuarioAutenticado actor, Usuario objetivo, String mensaje) {
		if (actor.usuarioId().equals(objetivo.getId())) {
			throw new ReglaNegocioException(mensaje + " Pide a otra persona de Promotoría o Dirección que lo haga.");
		}
	}

	private void exigirNoEsElUltimoPromotor(Usuario objetivo, String mensaje) {
		if (objetivo.isActivo() && objetivo.getRoles().contains(Rol.PROMOTOR)
				&& usuarios.bloquearActivosConRol(Rol.PROMOTOR).size() <= 1) {
			throw new ReglaNegocioException(mensaje);
		}
	}

	/** El motivo también se valida aquí: el formulario se puede saltar. */
	private static String motivo(String motivo) {
		String texto = motivo == null ? "" : motivo.strip();
		if (texto.length() < 10 || texto.length() > 500) {
			throw new ReglaNegocioException("El motivo debe tener entre 10 y 500 caracteres.");
		}
		return TextoSeguro.exigir(texto, "el motivo");
	}

	private static String detalle(Usuario usuario, String motivo) {
		return "Usuario " + usuario.getNombreUsuario() + ". Motivo: " + motivo;
	}

	private static String roles(Collection<Rol> roles) {
		return roles.stream().sorted().map(Rol::name).collect(Collectors.joining(","));
	}

	static String rolesTexto(Collection<Rol> roles) {
		return roles.stream().sorted().map(Rol::etiqueta).collect(Collectors.joining(" · "));
	}

	private static String estado(Usuario u, LocalDateTime ahora) {
		if (!u.isActivo()) {
			return "INACTIVO";
		}
		if (u.estaBloqueado(ahora)) {
			return "BLOQUEADO";
		}
		return u.isDebeCambiarClave() ? "CLAVE_PENDIENTE" : "ACTIVO";
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj);
	}
}
