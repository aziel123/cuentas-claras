package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

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
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CambiarRolesRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioCreado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioDetalle;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioResumen;
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
 *   <li>Jerarquía: Dirección no modifica usuarios de Promotoría ni de Dirección, ni asigna esos roles
 *       (intentarlo es una manipulación del formulario: responde 403).</li>
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

	private final UsuarioRepository usuarios;

	private final ServicioDetallesUsuario detalles;

	private final PasswordEncoder codificador;

	private final GeneradorClaveTemporal generador;

	private final AuditoriaService auditoria;

	private final SesionesUsuario sesiones;

	private final TransactionTemplate transaccion;

	private final PropiedadesSeguridad propiedades;

	private final Clock reloj;

	public ServicioUsuarios(UsuarioRepository usuarios, ServicioDetallesUsuario detalles, PasswordEncoder codificador,
			GeneradorClaveTemporal generador, AuditoriaService auditoria, SesionesUsuario sesiones,
			PropiedadesSeguridad propiedades, PlatformTransactionManager transacciones, Clock reloj) {
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
				u.getDesactivadoPor(), esUnoMismo, !esUnoMismo && puedeTocar(actor, u.getRoles()));
	}

	/** Roles que quien está en sesión puede asignar. */
	public Set<Rol> rolesAsignables() {
		Set<Rol> roles = EnumSet.allOf(Rol.class);
		if (!esPromotor(actor())) {
			roles.removeAll(ROLES_DIRECTIVOS);
		}
		return roles;
	}

	/**
	 * Crea el usuario con una clave temporal aleatoria que debe cambiar en su primer ingreso.
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
		String claveTemporal = generador.generar();
		Usuario usuario;
		try {
			usuario = transaccion.execute(estado -> {
				LocalDateTime ahora = ahora();
				Usuario nuevo = Usuario.nuevo(nombreUsuario, solicitud.nombreCompleto(), solicitud.correo(),
						codificador.encode(claveTemporal), roles);
				nuevo.vencerClaveTemporalEn(ahora.plus(propiedades.vigenciaClaveTemporal()));
				usuarios.save(nuevo);
				auditoria.registrar(AccionAuditoria.USUARIO_CREADO, "usuario", nuevo.getId().toString(), null,
						"roles=" + roles(nuevo.getRoles()) + "; activo; clave temporal",
						"Usuario " + nuevo.getNombreUsuario() + " (" + nuevo.getNombreCompleto() + ").");
				return nuevo;
			});
		}
		catch (DataIntegrityViolationException e) {
			// Otra persona creó el mismo nombre al mismo tiempo: lo detiene la restricción única de la base.
			throw nombreRepetido(nombreUsuario);
		}
		return new UsuarioCreado(usuario.getId(), usuario.getNombreUsuario(), usuario.getNombreCompleto(), claveTemporal);
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

	/** Genera una nueva clave temporal (se muestra una sola vez), desbloquea la cuenta y cierra sus sesiones. */
	@Transactional
	public UsuarioCreado restablecerClave(Long id, String motivo) {
		UsuarioAutenticado actor = actor();
		Usuario usuario = buscar(id);
		exigirNoEsUnoMismo(actor, usuario, "Para cambiar tu propia clave usa «Cambiar clave».");
		exigirPuedeTocar(actor, usuario);
		String texto = motivo(motivo);
		String claveTemporal = generador.generar();
		LocalDateTime ahora = ahora();
		usuario.restablecerClave(codificador.encode(claveTemporal), ahora, ahora.plus(propiedades.vigenciaClaveTemporal()),
				actor.getUsername());
		auditoria.registrar(AccionAuditoria.CLAVE_RESTABLECIDA, "usuario", id.toString(), null, "clave temporal",
				detalle(usuario, texto));
		sesiones.expirar(id);
		return new UsuarioCreado(usuario.getId(), usuario.getNombreUsuario(), usuario.getNombreCompleto(), claveTemporal);
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

	private static void exigirPuedeTocar(UsuarioAutenticado actor, Usuario objetivo) {
		if (!puedeTocar(actor, objetivo.getRoles())) {
			throw new AccessDeniedException("Dirección no puede modificar usuarios de Promotoría ni de Dirección");
		}
	}

	private static void exigirPuedeAsignar(UsuarioAutenticado actor, Collection<Rol> roles) {
		if (roles != null && !puedeTocar(actor, roles)) {
			throw new AccessDeniedException("Dirección no puede asignar los roles de Promotoría ni de Dirección");
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
		return texto;
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
