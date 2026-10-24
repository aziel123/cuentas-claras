package pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.CambioContactoTitular;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PrimeraPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.CambioContactoTitularRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.EnlaceActivacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.SesionUsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnvioEnlaceSolicitado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.GeneradorClaveTemporal;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioDetallesUsuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Sprint 8, tanda 1 (sección 3.3): el puerto con el que la plataforma crea la PRIMERA Promotoría de un colegio nuevo, una
 * sola vez por colegio. Es la transacción 3 del alta: corre con la conexión de {@code cc_sistema} (ruta de identidad) en el
 * colegio nuevo, conservando el {@code SecurityContext} del operador (la bitácora del colegio nuevo dice quién lo creó).
 * <ul>
 *   <li>La cuenta nace con una clave al azar ya vencida: solo se activa con el enlace de un solo uso que le llega al
 *       titular (y, al activar, confirma su DNI: {@code ServicioActivacionCuenta}).</li>
 *   <li>{@link PrimeraPromotoria} se escribe en la misma transacción, ANTES del rol: en MySQL, trg_primera_promotoria_nace
 *       exige la firma del operador ({@code primera_promotoria:{colegio}}, de hace 5 minutos o menos, en el colegio de
 *       plataforma) y trg_usuario_rol_alta exige esa fila para dar el primer PROMOTOR.</li>
 *   <li>Después de esto, el operador ya no puede crear cuentas en ese colegio (trg_usuario_nace).</li>
 * </ul>
 * Correcciones del sprint 8 (S8-A1 y QA-S8-3): el operador solo toca al titular mientras su cuenta NUNCA se activó
 * ({@link #cuentaActivada}: usó un enlace o abrió una sesión). Reenviar el enlace al mismo contacto lo hace un operador y
 * avisa al titular, al correo institucional y al correo externo de control; cambiar el contacto lo propone un operador y
 * lo aplica OTRO, con aviso al contacto anterior, al correo institucional y al correo de control en la misma transacción.
 */
@Component
@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
public class AltaTitular {

	/** El titular del contrato: su usuario, su nombre y su celular o correo (al menos uno). */
	public record DatosTitular(String nombreUsuario, String nombreCompleto, String telefonoWhatsapp, String correo) {
	}

	/** El operador que firma: su cuenta en el colegio de plataforma. */
	public record OperadorFirmante(Long usuarioId, Long plataformaId, String nombreUsuario, String correo) {

		public OperadorFirmante {
			Objects.requireNonNull(usuarioId, "usuarioId");
			Objects.requireNonNull(plataformaId, "plataformaId");
			Objects.requireNonNull(nombreUsuario, "nombreUsuario");
		}
	}

	/** Lo que el operador ve: a dónde se envió el enlace (enmascarado), nunca el enlace. */
	public record TitularCreado(Long usuarioId, String nombreUsuario, String canal, String destino) {
	}

	/** Lo que el operador ve de un cambio por aplicar: los contactos enmascarados, el motivo y quién lo propuso. */
	public record ContactoPorAplicar(Long id, String anterior, String nuevo, String motivo, String propuestoPor,
			LocalDateTime propuestoEn) {
	}

	private final UsuarioRepository usuarios;

	private final PrimeraPromotoriaRepository primeras;

	private final ServicioDetallesUsuario detalles;

	private final PasswordEncoder codificador;

	private final GeneradorClaveTemporal generador;

	private final AuditoriaService auditoria;

	private final EjecucionIdentidad identidad;

	private final ApplicationEventPublisher eventos;

	private final EnlacesActivacion enlaces;

	private final EnlaceActivacionRepository enlacesGuardados;

	private final SesionUsuarioRepository sesiones;

	private final CambioContactoTitularRepository cambios;

	private final TransactionTemplate lectura;

	private final Clock reloj;

	public AltaTitular(UsuarioRepository usuarios, PrimeraPromotoriaRepository primeras, ServicioDetallesUsuario detalles,
			PasswordEncoder codificador, GeneradorClaveTemporal generador, AuditoriaService auditoria,
			EjecucionIdentidad identidad, ApplicationEventPublisher eventos, EnlacesActivacion enlaces,
			EnlaceActivacionRepository enlacesGuardados, SesionUsuarioRepository sesiones,
			CambioContactoTitularRepository cambios, PlatformTransactionManager transacciones, Clock reloj) {
		this.usuarios = usuarios;
		this.primeras = primeras;
		this.detalles = detalles;
		this.codificador = codificador;
		this.generador = generador;
		this.auditoria = auditoria;
		this.identidad = identidad;
		this.eventos = eventos;
		this.enlaces = enlaces;
		this.enlacesGuardados = enlacesGuardados;
		this.sesiones = sesiones;
		this.cambios = cambios;
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.reloj = reloj;
	}

	/** Valida los datos del titular antes de crear nada (el nombre de usuario se busca en toda la plataforma). */
	public DatosTitular validar(DatosTitular titular, OperadorFirmante operador) {
		String nombreUsuario = Usuario.normalizarNombreUsuario(titular.nombreUsuario());
		if (!Usuario.esNombreUsuarioValido(nombreUsuario)) {
			throw new ReglaNegocioException("El usuario del titular debe tener de 3 a 60 caracteres: letras minúsculas sin "
					+ "tildes, números, punto, guion o guion bajo. Por ejemplo: maria.torres");
		}
		if (titular.nombreCompleto() == null || titular.nombreCompleto().isBlank()) {
			throw new ReglaNegocioException("Escribe el nombre completo del titular.");
		}
		String telefono = telefono(titular.telefonoWhatsapp());
		String correo = titular.correo() == null || titular.correo().isBlank() ? null : titular.correo().strip();
		if (telefono == null && correo == null) {
			throw new ReglaNegocioException("Escribe el celular (WhatsApp) o el correo del titular: ahí le llega el enlace "
					+ "para activar su cuenta. Nadie más ve ese enlace.");
		}
		if (correo != null && operador.correo() != null && correo.equalsIgnoreCase(operador.correo())) {
			throw new ReglaNegocioException("Ese correo es tuyo: el enlace debe llegar solo al titular del contrato.");
		}
		if (detalles.colegioDe(nombreUsuario).isPresent()) {
			throw new ReglaNegocioException("Ya existe un usuario «" + nombreUsuario + "» en la plataforma. Elige otro.");
		}
		return new DatosTitular(nombreUsuario, titular.nombreCompleto().strip(), telefono, correo);
	}

	/**
	 * Crea la cuenta del titular con el rol PROMOTOR y su marca de primera Promotoría, y le envía el enlace de activación
	 * (el mensaje nace en esta misma transacción; el enlace lo genera el envío). Debe llamarse SIN transacción abierta y
	 * después de firmar {@code primera_promotoria:{colegio}} en el colegio de plataforma.
	 */
	public TitularCreado crearPrimeraPromotoria(long colegioId, DatosTitular titular, OperadorFirmante operador) {
		DatosTitular datos = validar(titular, operador);
		try {
			return ContextoColegio.en(colegioId, () -> identidad.como(() -> {
				if (primeras.existsByIdNotNull()) {
					throw new ReglaNegocioException("Este colegio ya tiene su primera Promotoría: no se crea otra. Las cuentas "
							+ "siguientes las crea la Promotoría del colegio.");
				}
				if (usuarios.count() > 0) {
					throw new ReglaNegocioException("Este colegio ya tiene cuentas: el operador solo crea la primera.");
				}
				LocalDateTime ahora = ahora();
				Usuario nuevo = Usuario.nuevo(datos.nombreUsuario(), datos.nombreCompleto(), datos.correo(),
						codificador.encode(generador.generar()), Set.of(Rol.PROMOTOR));
				nuevo.asignarTelefonoWhatsapp(datos.telefonoWhatsapp());
				nuevo.vencerClaveTemporalEn(ahora);
				// INSERT de la cuenta ahora (IDENTITY); el rol se escribe en el flush, después de la marca.
				usuarios.save(nuevo);
				primeras.saveAndFlush(PrimeraPromotoria.de(nuevo.getId(), operador.usuarioId(), operador.plataformaId()));
				usuarios.saveAndFlush(nuevo);
				auditoria.registrar(AccionAuditoria.PRIMERA_PROMOTORIA_CREADA, "usuario", nuevo.getId().toString(), null,
						"roles=PROMOTOR; activo; enlace de activación al titular",
						"El operador " + operador.nombreUsuario() + " creó la primera cuenta de Promotoría del colegio: "
								+ nuevo.getNombreUsuario() + " (" + nuevo.getNombreCompleto() + "). Su enlace de un solo uso va a "
								+ destino(nuevo) + "; al activarla confirma su DNI. Nadie más ve el enlace.");
				eventos.publishEvent(new EnvioEnlaceSolicitado(nuevo.getId(), PropositoEnlace.PERSONAL, null));
				return new TitularCreado(nuevo.getId(), nuevo.getNombreUsuario(), canal(nuevo), destino(nuevo));
			}));
		}
		catch (DataIntegrityViolationException e) {
			throw new ReglaNegocioException("No se pudo crear la cuenta del titular: el usuario ya existe o el colegio ya "
					+ "tiene su primera Promotoría.");
		}
	}

	/**
	 * Correcciones del sprint 8 (S8-A1): reenvía el enlace al MISMO contacto del titular (por ejemplo, venció). Lo hace un
	 * operador, solo mientras la cuenta nunca se activó, y avisa en la misma transacción al titular, al correo institucional
	 * y al correo externo de control (si no hay a quién, no se reenvía). Los enlaces anteriores quedan anulados.
	 */
	public TitularCreado reenviarEnlace(long colegioId, OperadorFirmante operador) {
		return ContextoColegio.en(colegioId, () -> identidad.como(() -> {
			Usuario titular = titularSinActivar();
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.ENLACE_TITULAR_REENVIADO,
					List.of(operador.nombreUsuario(), destino(titular)), "usuario", titular.getId(),
					"E" + enlacesGuardados.findByUsuarioIdOrderByIdDesc(titular.getId()).size(), titular.getId(), true, true,
					false));
			enlaces.anularVigentes(titular.getId());
			auditoria.registrar(AccionAuditoria.TITULAR_ENLACE_REENVIADO, "usuario", titular.getId().toString(), null,
					destino(titular), "El operador " + operador.nombreUsuario() + " reenvió el enlace de activación al titular "
							+ titular.getNombreUsuario() + " (mismo contacto). Se avisó al titular, al correo institucional y "
							+ "al correo externo de control.");
			eventos.publishEvent(new EnvioEnlaceSolicitado(titular.getId(), PropositoEnlace.PERSONAL, null));
			return new TitularCreado(titular.getId(), titular.getNombreUsuario(), canal(titular), destino(titular));
		}));
	}

	/**
	 * El titular sin activar del colegio y el número que lleva la firma de su próxima propuesta de cambio de contacto. Valida
	 * ANTES de firmar lo que rechazaría la propuesta (la firma es única por clave: un intento fallido no se repite con la
	 * misma).
	 */
	public IntentoCambio siguienteIntentoDeCambio(long colegioId, String telefonoWhatsapp, String correo, String motivo,
			OperadorFirmante operador) {
		return siguienteIntentoDeCambio(colegioId, null, telefonoWhatsapp, correo, motivo, operador);
	}

	/**
	 * Sprint 11, tanda 1c (A1c-1): lo mismo para cualquier cuenta de Promotoría o Dirección del colegio que perdió su
	 * contacto ({@code cuenta}: su nombre de usuario; {@code null}, el titular sin activar).
	 */
	public IntentoCambio siguienteIntentoDeCambio(long colegioId, String cuenta, String telefonoWhatsapp, String correo,
			String motivo, OperadorFirmante operador) {
		String telefono = telefono(telefonoWhatsapp);
		String nuevoCorreo = correo == null || correo.isBlank() ? null : correo.strip();
		if (telefono == null && nuevoCorreo == null) {
			throw new ReglaNegocioException("Escribe el celular (WhatsApp) o el correo del titular.");
		}
		if (nuevoCorreo != null && operador.correo() != null && nuevoCorreo.equalsIgnoreCase(operador.correo())) {
			throw new ReglaNegocioException("Ese correo es tuyo: el enlace debe llegar solo al titular del contrato.");
		}
		String texto = motivo == null ? "" : motivo.strip();
		if (texto.length() < 10 || texto.length() > 500) {
			throw new ReglaNegocioException("Escribe el motivo del cambio (de 10 a 500 caracteres): por ejemplo, quién lo "
					+ "pidió y cómo lo confirmaste con el titular.");
		}
		return ContextoColegio.en(colegioId, () -> lectura.execute(t -> {
			Usuario titular = objetivo(cuenta);
			if (Objects.equals(telefono, titular.getTelefonoWhatsapp()) && Objects.equals(nuevoCorreo, titular.getCorreo())) {
				throw new ReglaNegocioException("Ese es el contacto que ya tiene esa cuenta: no hay nada que cambiar.");
			}
			if (cambios.findFirstByUsuarioIdAndEstado(titular.getId(), CambioContactoTitular.Estado.PROPUESTO).isPresent()) {
				throw new ReglaNegocioException("Ya hay un cambio del contacto del titular por aplicar: que otro operador lo "
						+ "aplique o lo rechace antes de proponer otro.");
			}
			return new IntentoCambio(titular.getId(), cambios.countByUsuarioId(titular.getId()) + 1);
		}));
	}

	/**
	 * Valida ANTES de firmar que ese operador puede aplicar (o, con {@code aplicar} en falso, rechazar) el cambio: sigue
	 * PROPUESTO, el titular no activó su cuenta y, para aplicarlo, no lo propuso él.
	 */
	public void validarResolucion(long colegioId, long cambioId, OperadorFirmante operador, boolean aplicar) {
		ContextoColegio.en(colegioId, () -> lectura.executeWithoutResult(t -> {
			CambioContactoTitular cambio = cambios.findById(cambioId)
					.orElseThrow(() -> new ReglaNegocioException("Ese cambio del contacto del titular no existe."));
			if (cambio.getEstado() != CambioContactoTitular.Estado.PROPUESTO) {
				throw new ReglaNegocioException("Este cambio de contacto ya no está por resolver.");
			}
			if (aplicar) {
				if (Objects.equals(operador.nombreUsuario(), cambio.getPropuestoPor())) {
					throw new ReglaNegocioException("Tú propusiste este cambio: lo aplica otro operador, con su propia sesión.");
				}
				objetivoDe(cambio);
			}
		}));
	}

	/** El titular y el número de la próxima propuesta de cambio de su contacto (la firma lleva los dos). */
	public record IntentoCambio(Long titularId, long numero) {
	}

	/**
	 * Correcciones del sprint 8 (S8-A1): un operador PROPONE cambiar el celular o el correo del titular, que no activó su
	 * cuenta. Debe llamarse sin transacción abierta y después de firmar
	 * {@code cambio_contacto_titular:{colegio}:{usuario}:{n}} en el colegio de plataforma (trg_cambio_contacto_titular_nace).
	 * No cambia nada todavía: lo aplica OTRO operador con {@link #aplicarContacto}.
	 */
	public Long proponerContacto(long colegioId, String telefonoWhatsapp, String correo, String motivo,
			OperadorFirmante operador) {
		return proponerContacto(colegioId, null, telefonoWhatsapp, correo, motivo, operador);
	}

	/** Sprint 11, tanda 1c (A1c-1): lo mismo para cualquier cuenta de Promotoría o Dirección ({@code cuenta}). */
	public Long proponerContacto(long colegioId, String cuenta, String telefonoWhatsapp, String correo, String motivo,
			OperadorFirmante operador) {
		String telefono = telefono(telefonoWhatsapp);
		String nuevoCorreo = correo == null || correo.isBlank() ? null : correo.strip();
		if (telefono == null && nuevoCorreo == null) {
			throw new ReglaNegocioException("Escribe el celular (WhatsApp) o el correo del titular.");
		}
		if (nuevoCorreo != null && operador.correo() != null && nuevoCorreo.equalsIgnoreCase(operador.correo())) {
			throw new ReglaNegocioException("Ese correo es tuyo: el enlace debe llegar solo al titular del contrato.");
		}
		return ContextoColegio.en(colegioId, () -> identidad.como(() -> {
			Usuario titular = objetivo(cuenta);
			if (Objects.equals(telefono, titular.getTelefonoWhatsapp()) && Objects.equals(nuevoCorreo, titular.getCorreo())) {
				throw new ReglaNegocioException("Ese es el contacto que ya tiene esa cuenta: no hay nada que cambiar.");
			}
			if (cambios.findFirstByUsuarioIdAndEstado(titular.getId(), CambioContactoTitular.Estado.PROPUESTO).isPresent()) {
				throw new ReglaNegocioException("Ya hay un cambio del contacto del titular por aplicar: que otro operador lo "
						+ "aplique o lo rechace antes de proponer otro.");
			}
			CambioContactoTitular cambio = cambios.saveAndFlush(CambioContactoTitular.propuesto(titular, telefono,
					nuevoCorreo, motivo, operador.usuarioId(), operador.plataformaId(), operador.nombreUsuario()));
			auditoria.registrar(AccionAuditoria.TITULAR_CONTACTO_PROPUESTO, "cambio_contacto_titular",
					cambio.getId().toString(), destino(titular), destinoDe(telefono, nuevoCorreo), "El operador "
							+ operador.nombreUsuario() + " propuso cambiar el contacto del titular " + titular.getNombreUsuario()
							+ " antes de la activación. Motivo: " + cambio.getMotivo() + ". Lo aplica otro operador.");
			return cambio.getId();
		}));
	}

	/**
	 * Correcciones del sprint 8 (S8-A1): OTRO operador aplica el cambio propuesto. En la misma transacción: avisa al
	 * contacto ANTERIOR del titular (por cada canal que tenía), al correo institucional y al correo externo de control
	 * (antes de cambiarlo: el aviso va al contacto registrado); cambia el contacto, anula los enlaces vigentes y envía uno
	 * nuevo. Debe llamarse sin transacción abierta y después de firmar {@code cambio_contacto_titular:{id}:APLICADO}.
	 */
	public TitularCreado aplicarContacto(long colegioId, long cambioId, OperadorFirmante operador) {
		return ContextoColegio.en(colegioId, () -> identidad.como(() -> {
			CambioContactoTitular cambio = cambios.bloquear(cambioId)
					.orElseThrow(() -> new ReglaNegocioException("Ese cambio del contacto del titular no existe."));
			Usuario titular = objetivoDe(cambio);
			// Sprint 11, tanda 1c (A1c-1): el titular sin activar recibe su enlace nuevo; otra cuenta directiva (o el
			// titular ya activado) solo cambia su contacto, con el aviso al anterior.
			boolean sinActivar = titular.isDebeCambiarClave() && !cuentaActivada(titular.getId());
			if (!titular.getId().equals(cambio.getUsuarioId())
					|| !Objects.equals(titular.getTelefonoWhatsapp(), cambio.getTelefonoAnterior())
					|| !Objects.equals(titular.getCorreo(), cambio.getCorreoAnterior())) {
				throw new ReglaNegocioException("El contacto del titular ya no es el de la propuesta: recházala y propón otra.");
			}
			cambio.aplicar(operador.nombreUsuario(), ahora());
			String anterior = destino(titular);
			String nuevo = destinoDe(cambio.getTelefonoNuevo(), cambio.getCorreoNuevo());
			eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CONTACTO_TITULAR_CORREGIDO,
					List.of(cambio.getPropuestoPor(), operador.nombreUsuario(), nuevo), "cambio_contacto_titular",
					cambio.getId(), null, titular.getId(), true, true, false));
			// El cambio queda APLICADO antes que la cuenta: trg_usuario_contacto exige esa fila (con su firma).
			cambios.saveAndFlush(cambio);
			if (sinActivar) {
				titular.corregirContactoDelTitular(cambio.getTelefonoNuevo(), cambio.getCorreoNuevo());
			}
			else {
				titular.corregirContactoPorDosOperadores(cambio.getTelefonoNuevo(), cambio.getCorreoNuevo());
			}
			usuarios.saveAndFlush(titular);
			enlaces.anularVigentes(titular.getId());
			auditoria.registrar(AccionAuditoria.TITULAR_CONTACTO_CORREGIDO, "usuario", titular.getId().toString(), anterior,
					nuevo, "El operador " + operador.nombreUsuario() + " aplicó el cambio del contacto del titular "
							+ titular.getNombreUsuario() + " que propuso " + cambio.getPropuestoPor() + " (motivo: "
							+ cambio.getMotivo() + "). Se avisó al contacto anterior (" + anterior + "), al correo "
							+ "institucional y al correo externo de control. Si el titular no lo pidió, es un incidente.");
			if (sinActivar) {
				eventos.publishEvent(new EnvioEnlaceSolicitado(titular.getId(), PropositoEnlace.PERSONAL, null));
			}
			return new TitularCreado(titular.getId(), titular.getNombreUsuario(), canal(titular), destino(titular));
		}));
	}

	/** La cuenta del cambio: el titular sin activar ({@code null}) o una cuenta directiva del colegio por su nombre. */
	private Usuario objetivo(String cuenta) {
		if (cuenta == null) {
			return titularSinActivar();
		}
		Usuario usuario = usuarios.findByNombreUsuario(Usuario.normalizarNombreUsuario(cuenta))
				.filter(u -> u.getApoderadoId() == null)
				.orElseThrow(() -> new ReglaNegocioException("Esa cuenta no existe en el colegio."));
		if (!usuario.getRoles().contains(Rol.PROMOTOR) && !usuario.getRoles().contains(Rol.DIRECTOR)) {
			throw new ReglaNegocioException("Los operadores cambian solo el contacto de una cuenta de Promotoría o Dirección "
					+ "(las demás lo cambian en el colegio, con la aprobación de una Promotoría).");
		}
		return usuario;
	}

	/** La cuenta de un cambio propuesto: el titular sin activar o la cuenta directiva del cambio. */
	private Usuario objetivoDe(CambioContactoTitular cambio) {
		Usuario usuario = usuarios.findById(cambio.getUsuarioId())
				.orElseThrow(() -> new ReglaNegocioException("La cuenta del cambio ya no existe."));
		if (!usuario.getRoles().contains(Rol.PROMOTOR) && !usuario.getRoles().contains(Rol.DIRECTOR)) {
			throw new ReglaNegocioException("La cuenta del cambio ya no es de Promotoría ni de Dirección: recházalo.");
		}
		return usuario;
	}

	/**
	 * Correcciones del sprint 8 (S8-A1): un operador rechaza (o quien lo propuso retira) el cambio, con su firma
	 * ({@code cambio_contacto_titular:{id}:RECHAZADO}). Debe llamarse sin transacción abierta.
	 */
	public void rechazarContacto(long colegioId, long cambioId, OperadorFirmante operador) {
		ContextoColegio.en(colegioId, () -> identidad.ejecutar(() -> {
			CambioContactoTitular cambio = cambios.bloquear(cambioId)
					.orElseThrow(() -> new ReglaNegocioException("Ese cambio del contacto del titular no existe."));
			cambio.rechazar(operador.nombreUsuario(), ahora());
			cambios.saveAndFlush(cambio);
			auditoria.registrar(AccionAuditoria.TITULAR_CONTACTO_RECHAZADO, "cambio_contacto_titular",
					cambio.getId().toString(), "PROPUESTO", "RECHAZADO", "El operador " + operador.nombreUsuario()
							+ " rechazó el cambio del contacto del titular que propuso " + cambio.getPropuestoPor() + ".");
		}));
	}

	/** El cambio del contacto del titular por aplicar, si hay (para la ficha del operador: contactos enmascarados). */
	public Optional<ContactoPorAplicar> contactoPorAplicar(long colegioId) {
		// Sprint 11, tanda 1c (A1c-1): el del titular o el de cualquier cuenta directiva del colegio.
		return ContextoColegio.en(colegioId, () -> lectura.execute(t -> cambios
				.findFirstByEstadoOrderByIdAsc(CambioContactoTitular.Estado.PROPUESTO)
				.map(c -> new ContactoPorAplicar(c.getId(), destinoDe(c.getTelefonoAnterior(), c.getCorreoAnterior()),
						destinoDe(c.getTelefonoNuevo(), c.getCorreoNuevo()), c.getMotivo(), c.getPropuestoPor(),
						c.getCreadoEn()))));
	}

	/**
	 * Correcciones del sprint 8 (QA-S8-3): la cuenta se activó alguna vez: usó un enlace de activación o abrió una sesión.
	 * No se decide por «debe cambiar la clave»: un restablecimiento vuelve a pedirlo, pero la cuenta ya era de su titular.
	 * trg_usuario_contacto y trg_colegio_cambio usan la misma regla (cc_cuenta_activada). Con el colegio en contexto.
	 */
	public boolean cuentaActivada(Long usuarioId) {
		return enlacesGuardados.existsByUsuarioIdAndUsadoEnIsNotNull(usuarioId) || sesiones.existsByUsuarioId(usuarioId);
	}

	/** Si el titular del colegio en contexto ya activó su cuenta (sin titular: no). */
	public boolean titularActivado() {
		return primeras.findFirstByIdNotNull().map(p -> cuentaActivada(p.getUsuarioId())).orElse(false);
	}

	/** El titular (la cuenta de la primera Promotoría del colegio en contexto), que NUNCA activó su cuenta. */
	public Usuario titularSinActivar() {
		Usuario titular = primeras.findFirstByIdNotNull().flatMap(p -> usuarios.findById(p.getUsuarioId()))
				.orElseThrow(() -> new ReglaNegocioException("Este colegio todavía no tiene su primera Promotoría: completa el alta."));
		if (cuentaActivada(titular.getId())) {
			throw new ReglaNegocioException("El titular ya activó su cuenta: el operador ya no toca su contacto. Lo cambia él "
					+ "desde «Mi celular y correo», con la aprobación de otra persona.");
		}
		return titular;
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

	private static String canal(Usuario usuario) {
		return usuario.getTelefonoWhatsapp() != null ? "WhatsApp" : "correo";
	}

	private static String destino(Usuario usuario) {
		return destinoDe(usuario.getTelefonoWhatsapp(), usuario.getCorreo());
	}

	private static String destinoDe(String telefono, String correo) {
		return telefono != null ? "WhatsApp " + Enmascarar.telefono(telefono)
				: correo != null ? "correo " + Enmascarar.correo(correo) : "sin contacto";
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj);
	}
}
