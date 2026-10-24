package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.DerivadorSecreto;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContactoNormal;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSeguridad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.CrearUsuarioRequest;
import pe.edu.virgenmaria.cuentasclaras.seguridad.dto.UsuarioCreado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.MotivoCierreSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.UsoFacultadTitular;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.UsoFacultadTitular.Accion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.EnlaceActivacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.SesionUsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsoFacultadTitularRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 11, tanda 1 (sección 3.12, AP-1 y AP-2): la facultad del titular del contrato. Un colegio nuevo nace con una sola
 * persona (el titular, la cuenta de su primera Promotoría) y no podría armar su equipo directivo, porque dar Promotoría o
 * Dirección pide la aprobación de otra persona que no existe. Con su facultad, el titular:
 * <ul>
 *   <li>crea una cuenta del personal que nace con PROMOTOR o DIRECTOR (más sus otros roles permitidos);</li>
 *   <li>da PROMOTOR o DIRECTOR a una cuenta que ya existe; o los quita (solo en preparación: en producción, quitar va
 *       siempre por {@code CAMBIO_ROLES}, AP-1);</li>
 * </ul>
 * sin segunda aprobación, con la firma de su sesión ({@code facultad_titular:{colegio}:{n}}) y el código de 6 dígitos que
 * le llega por WhatsApp (o correo) en cada uso (AP-2). En PRODUCCION vale solo mientras no haya otra persona activa de
 * Promotoría o Dirección además del titular y de la cuenta destino (decisión 201). No vale sobre sí mismo, con la facultad
 * bloqueada por dos operadores, sin correo externo de control ni en un colegio que no opera. Una cuenta que todavía no se
 * activó necesita un contacto distinto de los del titular (no se crea «otra persona»). Las combinaciones prohibidas
 * («quien cobra no aprueba») siguen.
 * <p>
 * Cada uso deja, en la MISMA transacción: la fila de {@code uso_facultad_titular}, el cambio de roles, el evento resaltado
 * {@code FACULTAD_TITULAR_USADA} y el aviso al correo de control, a toda la Promotoría y a la cuenta que recibe o pierde el
 * rol (si no se puede avisar, no se guarda). Después, en el colegio de plataforma, el aviso a los operadores y su evento.
 * Todo por la ruta de identidad ({@code cc_sistema}); en MySQL lo exigen trg_uso_facultad_titular_nace y trg_usuario_rol_*.
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
public class FacultadTitular {

	private static final Logger LOG = LoggerFactory.getLogger(FacultadTitular.class);

	/** Cuánto vale el código (la base exige el mensaje de hace 10 minutos o menos). */
	public static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);

	private static final Set<Rol> DIRECTIVOS = EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR);

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	/** Lo que muestra la pantalla: si es el titular y si la facultad está disponible (y si no, por qué). */
	public record Estado(boolean titular, boolean disponible, String motivo, boolean enProduccion) {
	}

	/**
	 * El código pedido (vive en la sesión HTTP del titular hasta que lo usa): el mensaje que lo llevó y a dónde salió. No
	 * guarda el código.
	 */
	public record CodigoPedido(Long colegioId, Long titularId, Long mensajeId, String clave, LocalDateTime creadoEn,
			String destino) implements java.io.Serializable {
	}

	private final UsuarioRepository usuarios;

	private final PrimeraPromotoriaRepository primeras;

	private final SesionUsuarioRepository sesionesGuardadas;

	private final EnlaceActivacionRepository enlaces;

	private final UsoFacultadTitularRepository usos;

	private final ColegioRepository colegios;

	private final CorreoControlRegistrado correoControl;

	private final EjecucionIdentidad identidad;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final DerivadorSecreto derivador;

	private final PasswordEncoder codificador;

	private final GeneradorClaveTemporal generador;

	private final ServicioDetallesUsuario detalles;

	private final SesionesFirmadas sesionesFirmadas;

	private final SesionesUsuario sesionesHttp;

	private final PropiedadesSeguridad propiedades;

	private final TransactionTemplate lectura;

	private final Clock reloj;

	private final boolean demostracion;

	public FacultadTitular(UsuarioRepository usuarios, PrimeraPromotoriaRepository primeras,
			SesionUsuarioRepository sesionesGuardadas, EnlaceActivacionRepository enlaces, UsoFacultadTitularRepository usos,
			ColegioRepository colegios, CorreoControlRegistrado correoControl, EjecucionIdentidad identidad,
			FirmaSesion firma, AuditoriaService auditoria, ApplicationEventPublisher eventos, DerivadorSecreto derivador,
			PasswordEncoder codificador, GeneradorClaveTemporal generador, ServicioDetallesUsuario detalles,
			SesionesFirmadas sesionesFirmadas, SesionesUsuario sesionesHttp, PropiedadesSeguridad propiedades,
			PlatformTransactionManager transacciones, Clock reloj, Environment entorno) {
		this.usuarios = usuarios;
		this.primeras = primeras;
		this.sesionesGuardadas = sesionesGuardadas;
		this.enlaces = enlaces;
		this.usos = usos;
		this.colegios = colegios;
		this.correoControl = correoControl;
		this.identidad = identidad;
		this.firma = firma;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.derivador = derivador;
		this.codificador = codificador;
		this.generador = generador;
		this.detalles = detalles;
		this.sesionesFirmadas = sesionesFirmadas;
		this.sesionesHttp = sesionesHttp;
		this.propiedades = propiedades;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.reloj = reloj;
		List<String> perfiles = Arrays.asList(entorno.getActiveProfiles());
		// Solo en dev y en la demo (mensajería simulada, sin WhatsApp real): la pantalla muestra el código. Nunca en el
		// piloto ni en prod.
		this.demostracion = (perfiles.contains("dev") || perfiles.contains("demo")) && !perfiles.contains("prod")
				&& !perfiles.contains("piloto");
	}

	/** Si quien está en sesión es el titular y puede usar su facultad ahora (para la pantalla). */
	public Estado estado() {
		UsuarioAutenticado actor = actor();
		return lectura.execute(t -> {
			Optional<Usuario> titular = titularSiEs(actor);
			if (titular.isEmpty()) {
				return new Estado(false, false, null, false);
			}
			Colegio colegio = colegio();
			String motivo = motivoNoDisponible(colegio);
			return new Estado(true, motivo == null, motivo, colegio.getEstado() == EstadoColegio.PRODUCCION);
		});
	}

	/** Si quien está en sesión es el titular del contrato (con su facultad o sin ella). */
	public boolean esTitular() {
		UsuarioAutenticado actor = actor();
		return Boolean.TRUE.equals(lectura.execute(t -> titularSiEs(actor).isPresent()));
	}

	/**
	 * El titular pide el código de un uso: le llega al contacto registrado de su cuenta y vale 10 minutos. Devuelve el
	 * pedido (sin el código), que la pantalla guarda en la sesión.
	 */
	public CodigoPedido pedirCodigo() {
		UsuarioAutenticado actor = actor();
		return identidad.como(() -> {
			Usuario titular = exigirTitularDisponible(actor);
			LocalDateTime ahora = LocalDateTime.now(reloj);
			CodigoFacultadPedido pedido = CodigoFacultadPedido.de(titular.getId(), "CODIGO_FACULTAD:"
					+ UUID.randomUUID().toString().replace("-", "").substring(0, 16), ahora.plus(VIGENCIA_CODIGO).format(HORA));
			eventos.publishEvent(pedido);
			CodigoFacultadPedido.Resultado r = pedido.resultado();
			if (r.mensajeId() == null) {
				throw new IllegalStateException("La mensajería no creó el mensaje del código");
			}
			auditoria.registrar(AccionAuditoria.CODIGO_FACULTAD_PEDIDO, "usuario", titular.getId().toString(), null,
					r.destino(), "El titular pidió el código para usar su facultad: salió a " + r.destino()
							+ ". Vale 10 minutos y una sola vez.");
			return new CodigoPedido(ContextoColegio.actual(), titular.getId(), r.mensajeId(), r.clave(), r.creadoEn(),
					r.destino());
		});
	}

	/** Solo en dev y en la demo (sin WhatsApp real): el código del pedido, para mostrarlo en la pantalla. */
	public Optional<String> codigoDeDemostracion(CodigoPedido pedido) {
		return demostracion && pedido != null ? Optional.of(derivador.codigoFacultad(pedido.colegioId(), pedido.clave()))
				: Optional.empty();
	}

	/**
	 * Crea una cuenta del personal que nace con UN rol directivo (PROMOTOR o DIRECTOR) más sus otros roles permitidos,
	 * con el código del titular. La cuenta se activa con su enlace de un solo uso, que va a SU contacto.
	 */
	public UsuarioCreado crearCuentaDirectiva(CrearUsuarioRequest solicitud, String codigo, CodigoPedido pedido) {
		UsuarioAutenticado actor = actor();
		Set<Rol> roles = solicitud.roles() == null ? EnumSet.noneOf(Rol.class) : EnumSet.copyOf(solicitud.roles());
		Set<Rol> directivos = EnumSet.copyOf(roles);
		directivos.retainAll(DIRECTIVOS);
		if (directivos.size() != 1) {
			throw new ReglaNegocioException("Con tu facultad, cada cuenta nace con UN rol directivo (Promotoría o Dirección): "
					+ "es un código por cada uso.");
		}
		if (roles.contains(Rol.APODERADO) || roles.contains(Rol.OPERADOR_PLATAFORMA)) {
			throw new ReglaNegocioException("Esa cuenta no se crea desde aquí.");
		}
		Rol directivo = directivos.iterator().next();
		String nombreUsuario = Usuario.normalizarNombreUsuario(solicitud.nombreUsuario());
		if (!Usuario.esNombreUsuarioValido(nombreUsuario)) {
			throw new ReglaNegocioException("El usuario debe tener de 3 a 60 caracteres: letras minúsculas sin tildes, "
					+ "números, punto, guion o guion bajo. Por ejemplo: lucia.ramos");
		}
		if (detalles.colegioDe(nombreUsuario).isPresent()) {
			throw new ReglaNegocioException("Ya existe un usuario «" + nombreUsuario + "» en la plataforma. Elige otro.");
		}
		String telefono = telefono(solicitud.telefonoWhatsapp());
		String correo = solicitud.correo() == null || solicitud.correo().isBlank() ? null : solicitud.correo().strip();
		if (telefono == null && correo == null) {
			throw new ReglaNegocioException("Escribe el celular (WhatsApp) o el correo de la persona: ahí le llega el enlace "
					+ "para activar su cuenta. Nadie más ve ese enlace.");
		}
		Usuario creado;
		try {
			creado = identidad.como(() -> {
				Usuario titular = exigirTitularDisponible(actor);
				exigirCodigo(titular, pedido, codigo);
				exigirOtroContacto(titular, telefono, correo);
				exigirSinOtraDirectivaEnProduccion(titular.getId(), null);
				LocalDateTime ahora = LocalDateTime.now(reloj);
				// La firma antes que la cuenta: firmar vacía la sesión de Hibernate, y los roles de la cuenta nueva no deben
				// llegar a la base antes que su uso de la facultad (trg_usuario_rol_alta lo exige).
				int numero = firmarUso();
				Usuario nuevo = Usuario.nuevo(nombreUsuario, solicitud.nombreCompleto(), correo,
						codificador.encode(generador.generar()), roles);
				nuevo.asignarTelefonoWhatsapp(telefono);
				nuevo.vencerClaveTemporalEn(ahora);
				// La cuenta primero (su id lo necesita el uso); el uso antes que sus filas de usuario_rol (con IDENTITY,
				// Hibernate inserta la cuenta y el uso al guardarlos, y los roles recién al vaciar la sesión).
				usuarios.save(nuevo);
				UsoFacultadTitular uso = guardarUso(numero, titular, nuevo.getId(), directivo, Accion.DAR, true, pedido);
				usuarios.saveAndFlush(nuevo);
				auditoria.registrar(AccionAuditoria.USUARIO_CREADO, "usuario", nuevo.getId().toString(), null,
						"roles=" + rolesNombres(roles) + "; activo; enlace de activación al titular", "Usuario "
								+ nuevo.getNombreUsuario() + " (" + nuevo.getNombreCompleto() + "), creado por el titular con su "
								+ "facultad (uso " + uso.getNumero() + "). Su enlace de un solo uso va a "
								+ destinoEnmascarado(nuevo) + ": nadie más lo ve.");
				eventos.publishEvent(new EnvioEnlaceSolicitado(nuevo.getId(), PropositoEnlace.PERSONAL, null));
				avisar(titular, nuevo, directivo, Accion.DAR, uso);
				return nuevo;
			});
		}
		catch (DataIntegrityViolationException e) {
			throw new ReglaNegocioException("No se pudo crear la cuenta: el usuario ya existe o el código ya se usó. Pide un "
					+ "código nuevo.");
		}
		avisarOperadores(actor, creado.getNombreUsuario(), directivo, Accion.DAR);
		return new UsuarioCreado(creado.getId(), creado.getNombreUsuario(), creado.getNombreCompleto(),
				creado.getTelefonoWhatsapp() != null ? "WhatsApp" : "correo",
				creado.getTelefonoWhatsapp() != null ? Enmascarar.telefono(creado.getTelefonoWhatsapp())
						: Enmascarar.correo(creado.getCorreo()),
				LocalDateTime.now(reloj).plus(propiedades.vigenciaClaveTemporal()));
	}

	/** Da PROMOTOR o DIRECTOR a una cuenta del personal que ya existe, con el código del titular. */
	public void dar(Long usuarioId, Rol rol, String codigo, CodigoPedido pedido) {
		cambiar(usuarioId, rol, Accion.DAR, codigo, pedido);
	}

	/** Quita PROMOTOR o DIRECTOR a otra cuenta (solo en preparación: en producción, por CAMBIO_ROLES). */
	public void quitar(Long usuarioId, Rol rol, String codigo, CodigoPedido pedido) {
		cambiar(usuarioId, rol, Accion.QUITAR, codigo, pedido);
	}

	private void cambiar(Long usuarioId, Rol rol, Accion accion, String codigo, CodigoPedido pedido) {
		UsuarioAutenticado actor = actor();
		if (rol != Rol.PROMOTOR && rol != Rol.DIRECTOR) {
			throw new ReglaNegocioException("La facultad del titular es solo para Promotoría y Dirección.");
		}
		String nombre = identidad.como(() -> {
			Usuario titular = exigirTitularDisponible(actor);
			Usuario cuenta = usuarios.findById(usuarioId)
					.orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
			if (cuenta.getId().equals(titular.getId())) {
				throw new ReglaNegocioException("Tu facultad no se usa sobre tu propia cuenta: ni para darte Dirección ni para "
						+ "quitarte Promotoría.");
			}
			if (cuenta.getApoderadoId() != null) {
				throw new ReglaNegocioException("Esa es la cuenta de un apoderado: no recibe roles del personal.");
			}
			Set<Rol> nuevos = cuenta.getRoles().isEmpty() ? EnumSet.noneOf(Rol.class) : EnumSet.copyOf(cuenta.getRoles());
			if (accion == Accion.DAR) {
				if (nuevos.contains(rol)) {
					throw new ReglaNegocioException("Esa cuenta ya tiene " + rol.etiqueta() + ".");
				}
				nuevos.add(rol);
			}
			else {
				if (!nuevos.contains(rol)) {
					throw new ReglaNegocioException("Esa cuenta no tiene " + rol.etiqueta() + ".");
				}
				if (colegio().getEstado() == EstadoColegio.PRODUCCION) {
					throw new ReglaNegocioException("En producción, quitar Promotoría o Dirección se pide con «Cambiar roles» y "
							+ "lo aprueba otra persona: nunca se quita solo al contrapeso.");
				}
				nuevos.remove(rol);
				if (nuevos.isEmpty()) {
					throw new ReglaNegocioException("La cuenta se quedaría sin roles: dale otro rol primero o desactívala.");
				}
			}
			// Quien cobra no aprueba (rige también para el titular): antes de firmar.
			pe.edu.virgenmaria.cuentasclaras.seguridad.model.ReglasSegregacion.validarCuenta(nuevos, null);
			exigirCodigo(titular, pedido, codigo);
			if (accion == Accion.DAR) {
				exigirSinOtraDirectivaEnProduccion(titular.getId(), cuenta.getId());
				if (!cuentaActivada(cuenta.getId())) {
					exigirOtroContacto(titular, cuenta.getTelefonoWhatsapp(), cuenta.getCorreo());
				}
			}
			String anteriores = rolesNombres(cuenta.getRoles());
			UsoFacultadTitular uso = guardarUso(firmarUso(), titular, cuenta.getId(), rol, accion, false, pedido);
			cuenta.cambiarRoles(nuevos);
			usuarios.saveAndFlush(cuenta);
			auditoria.registrar(AccionAuditoria.ROLES_CAMBIADOS, "usuario", cuenta.getId().toString(), anteriores,
					rolesNombres(nuevos), "Usuario " + cuenta.getNombreUsuario() + ": el titular "
							+ (accion == Accion.DAR ? "le dio " : "le quitó ") + rol.etiqueta() + " con su facultad (uso "
							+ uso.getNumero() + "), sin segunda aprobación.");
			sesionesFirmadas.cerrarDe(cuenta.getId(), MotivoCierreSesion.CUENTA_CAMBIADA);
			avisar(titular, cuenta, rol, accion, uso);
			return cuenta.getNombreUsuario();
		});
		sesionesHttp.expirar(usuarioId);
		avisarOperadores(actor, nombre, rol, accion);
	}

	/** La firma de la sesión del titular para el uso siguiente (numerado sin huecos); devuelve su número. */
	private int firmarUso() {
		int numero = (int) usos.count() + 1;
		firma.firmar(ClaveFirma.facultadTitular(ContextoColegio.actual(), numero));
		return numero;
	}

	/** La fila del uso, ya firmado. */
	private UsoFacultadTitular guardarUso(int numero, Usuario titular, Long usuarioId, Rol rol, Accion accion,
			boolean cuentaNueva, CodigoPedido pedido) {
		return usos.saveAndFlush(UsoFacultadTitular.de(numero, titular, usuarioId, rol, accion, cuentaNueva,
				pedido.mensajeId()));
	}

	/**
	 * El evento resaltado y los avisos de la MISMA transacción: el correo de control, toda la Promotoría y la cuenta que
	 * recibe o pierde el rol.
	 */
	private void avisar(Usuario titular, Usuario cuenta, Rol rol, Accion accion, UsoFacultadTitular uso) {
		String hizo = accion == Accion.DAR ? "le dio" : "le quitó";
		auditoria.registrar(AccionAuditoria.FACULTAD_TITULAR_USADA, "uso_facultad_titular", uso.getId().toString(), null,
				accion.name() + " " + rol.name() + " a " + cuenta.getNombreUsuario(), "El titular " + titular.getNombreUsuario()
						+ " " + hizo + " " + rol.etiqueta() + " a " + cuenta.getNombreUsuario() + " con su facultad, sin "
						+ "segunda aprobación (uso " + uso.getNumero() + ", con su firma y su código). Se avisó al correo de "
						+ "control, a toda la Promotoría y a la plataforma.");
		eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.FACULTAD_TITULAR_USADA,
				List.of(titular.getNombreCompleto(), hizo.replace("le ", ""), rol.etiqueta(), cuenta.getNombreUsuario()),
				"uso_facultad_titular", uso.getId(), null, cuenta.getId(), false, true, true));
	}

	/**
	 * Después, en el colegio de plataforma (es otro colegio: otra transacción), el aviso a los operadores y su evento en
	 * la bitácora de la plataforma. Si falla, queda en el log: el testigo que impide el cambio en silencio ya recibió su
	 * aviso en la transacción del uso.
	 */
	private void avisarOperadores(UsuarioAutenticado actor, String cuenta, Rol rol, Accion accion) {
		Long colegioId = ContextoColegio.actual();
		try {
			Long plataforma = lectura.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA).map(Colegio::getId)
					.orElse(null));
			String colegio = lectura.execute(t -> colegios.findById(colegioId).map(Colegio::getNombre).orElse("?"));
			if (plataforma == null) {
				return;
			}
			ContextoColegio.en(plataforma, () -> identidad.ejecutar(() -> {
				eventos.publishEvent(new AvisoOperadores(plataforma, AvisoPlataforma.Tipo.FACULTAD_TITULAR_USADA,
						List.of(actor.nombreCompleto() + " (" + colegio + ")", accion == Accion.DAR ? "dio" : "quitó",
								rol.etiqueta(), cuenta), "colegio", colegioId, accion.name() + ":" + cuenta + ":" + rol.name()
										+ ":" + LocalDateTime.now(reloj).withNano(0)));
				auditoria.registrar(AccionAuditoria.FACULTAD_TITULAR_USADA, "colegio", colegioId.toString(), null,
						accion.name() + " " + rol.name() + " a " + cuenta, "El titular del colegio " + colegio + " "
								+ (accion == Accion.DAR ? "dio " : "quitó ") + rol.etiqueta() + " a " + cuenta
								+ " con su facultad.");
			}));
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo avisar a los operadores del uso de la facultad en el colegio {}: {}", colegioId,
					e.getClass().getSimpleName());
		}
	}

	/** El titular en sesión, con su facultad disponible; si no, el motivo. */
	private Usuario exigirTitularDisponible(UsuarioAutenticado actor) {
		Usuario titular = titularSiEs(actor).orElseThrow(() -> new AccessDeniedException(
				"Solo el titular del contrato (la primera Promotoría del colegio) usa esta facultad."));
		String motivo = motivoNoDisponible(colegio());
		if (motivo != null) {
			throw new ReglaNegocioException(motivo);
		}
		return titular;
	}

	/** La cuenta del titular si es quien está en sesión: activa, activada y con PROMOTOR. */
	private Optional<Usuario> titularSiEs(UsuarioAutenticado actor) {
		return primeras.findFirstByIdNotNull().filter(p -> p.getUsuarioId().equals(actor.usuarioId()))
				.flatMap(p -> usuarios.findById(p.getUsuarioId()))
				.filter(u -> u.isActivo() && u.getApoderadoId() == null && u.getRoles().contains(Rol.PROMOTOR)
						&& cuentaActivada(u.getId()));
	}

	/** Por qué no se puede usar ahora ({@code null} si se puede). */
	private String motivoNoDisponible(Colegio colegio) {
		if (!colegio.getEstado().opera()) {
			return "El colegio no está operando: la facultad del titular no se usa.";
		}
		if (colegio.isFacultadBloqueada()) {
			return "Dos operadores de la plataforma bloquearon tu facultad. Pide Promotoría o Dirección con «Cambiar roles»; "
					+ "lo aprueba otra persona.";
		}
		if (!correoControl.registrado()) {
			return "Tu colegio todavía no tiene su correo externo de control (lo registra el operador): sin ese testigo no hay "
					+ "facultad del titular.";
		}
		return null;
	}

	/** El código: del mensaje que le llegó al titular en este colegio, de hace 10 minutos o menos y sin usar. */
	private void exigirCodigo(Usuario titular, CodigoPedido pedido, String codigo) {
		if (pedido == null || !Objects.equals(pedido.colegioId(), ContextoColegio.actual())
				|| !Objects.equals(pedido.titularId(), titular.getId())) {
			throw new ReglaNegocioException("Primero pide tu código: te llega por WhatsApp o correo y vale 10 minutos.");
		}
		if (pedido.creadoEn() == null || pedido.creadoEn().plus(VIGENCIA_CODIGO).isBefore(LocalDateTime.now(reloj))) {
			throw new ReglaNegocioException("Tu código venció (vale 10 minutos): pide uno nuevo.");
		}
		if (usos.existsByMensajeId(pedido.mensajeId())) {
			throw new ReglaNegocioException("Ese código ya se usó: pide uno nuevo para cada cambio.");
		}
		String esperado = derivador.codigoFacultad(pedido.colegioId(), pedido.clave());
		String escrito = codigo == null ? "" : codigo.replaceAll("\\s", "");
		if (!MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), escrito.getBytes(StandardCharsets.UTF_8))) {
			throw new ReglaNegocioException("El código no coincide con el que te llegó. Revísalo o pide uno nuevo.");
		}
	}

	/** AP-2: una cuenta que todavía no se activó no va al contacto del titular (no se crea «otra persona»). */
	private static void exigirOtroContacto(Usuario titular, String telefono, String correo) {
		for (String suyo : new String[] { titular.getTelefonoWhatsapp(), titular.getCorreo() }) {
			if (suyo == null || suyo.isBlank()) {
				continue;
			}
			if ((telefono != null && ContactoNormal.iguales(telefono, suyo))
					|| (correo != null && ContactoNormal.iguales(correo, suyo))) {
				throw new ReglaNegocioException("Ese celular o correo es tuyo: la cuenta de otra persona se activa con SU "
						+ "contacto, nunca con el del titular.");
			}
		}
	}

	/** Decisión 201: en producción, la facultad da roles solo sin otra persona directiva (además del titular y del destino). */
	private void exigirSinOtraDirectivaEnProduccion(Long titularId, Long destinoId) {
		if (colegio().getEstado() != EstadoColegio.PRODUCCION) {
			return;
		}
		boolean otra = DIRECTIVOS.stream().flatMap(r -> usuarios.activosConRol(r).stream())
				.anyMatch(u -> u.getApoderadoId() == null && !u.getId().equals(titularId) && !u.getId().equals(destinoId));
		if (otra) {
			throw new ReglaNegocioException("El colegio está en producción y ya tiene otra persona de Promotoría o Dirección: "
					+ "pide el rol con «Cambiar roles»; lo aprueba esa otra persona.");
		}
	}

	private boolean cuentaActivada(Long usuarioId) {
		return enlaces.existsByUsuarioIdAndUsadoEnIsNotNull(usuarioId) || sesionesGuardadas.existsByUsuarioId(usuarioId);
	}

	private Colegio colegio() {
		Long colegioId = ContextoColegio.actual();
		return colegios.findById(colegioId).filter(c -> !c.esPlataforma())
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
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

	private static String destinoEnmascarado(Usuario u) {
		return u.getTelefonoWhatsapp() != null ? "WhatsApp " + Enmascarar.telefono(u.getTelefonoWhatsapp())
				: "correo " + Enmascarar.correo(u.getCorreo());
	}

	private static String rolesNombres(java.util.Collection<Rol> roles) {
		return roles.stream().sorted().map(Rol::name).collect(java.util.stream.Collectors.joining(","));
	}

	private static UsuarioAutenticado actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere un usuario en sesión");
	}

	/** Para las pruebas: el formato del código (6 dígitos). */
	static boolean pareceCodigo(String codigo) {
		return codigo != null && codigo.toLowerCase(Locale.ROOT).matches("\\d{6}");
	}
}
