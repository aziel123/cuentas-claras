package pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Enmascarar;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.Telefono;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PrimeraPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.PropositoEnlace;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnvioEnlaceSolicitado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.GeneradorClaveTemporal;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioDetallesUsuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Sprint 8, tanda 1 (sección 3.3): el puerto con el que la plataforma crea la PRIMERA Promotoría de un colegio nuevo, una
 * sola vez por colegio, y corrige su contacto antes de la activación. Es la transacción 3 del alta: corre con la conexión
 * de {@code cc_sistema} (ruta de identidad) en el colegio nuevo, conservando el {@code SecurityContext} del operador (la
 * bitácora del colegio nuevo dice quién lo creó).
 * <ul>
 *   <li>La cuenta nace con una clave al azar ya vencida: solo se activa con el enlace de un solo uso que le llega al
 *       titular (y, al activar, confirma su DNI: {@code ServicioActivacionCuenta}).</li>
 *   <li>{@link PrimeraPromotoria} se escribe en la misma transacción, ANTES del rol: en MySQL, trg_primera_promotoria_nace
 *       exige la firma del operador ({@code primera_promotoria:{colegio}}, de hace 5 minutos o menos, en el colegio de
 *       plataforma) y trg_usuario_rol_alta exige esa fila para dar el primer PROMOTOR.</li>
 *   <li>Después de esto, el operador ya no puede crear cuentas en ese colegio (trg_usuario_nace).</li>
 * </ul>
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

	private final UsuarioRepository usuarios;

	private final PrimeraPromotoriaRepository primeras;

	private final ServicioDetallesUsuario detalles;

	private final PasswordEncoder codificador;

	private final GeneradorClaveTemporal generador;

	private final AuditoriaService auditoria;

	private final EjecucionIdentidad identidad;

	private final ApplicationEventPublisher eventos;

	private final EnlacesActivacion enlaces;

	private final Clock reloj;

	public AltaTitular(UsuarioRepository usuarios, PrimeraPromotoriaRepository primeras, ServicioDetallesUsuario detalles,
			PasswordEncoder codificador, GeneradorClaveTemporal generador, AuditoriaService auditoria,
			EjecucionIdentidad identidad, ApplicationEventPublisher eventos, EnlacesActivacion enlaces, Clock reloj) {
		this.usuarios = usuarios;
		this.primeras = primeras;
		this.detalles = detalles;
		this.codificador = codificador;
		this.generador = generador;
		this.auditoria = auditoria;
		this.identidad = identidad;
		this.eventos = eventos;
		this.enlaces = enlaces;
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
	 * Corrige el celular o el correo del titular y vuelve a enviar su enlace, SOLO mientras la cuenta no esté activada.
	 * En MySQL, trg_usuario_contacto exige la firma del operador ({@code titular_contacto:{colegio}:{usuario}:{version}}
	 * en el colegio de plataforma) y que la cuenta sea la del titular, sin activar, de un colegio en preparación.
	 */
	public TitularCreado corregirContacto(long colegioId, String telefonoWhatsapp, String correo,
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
			Usuario titular = titularSinActivar();
			String anterior = destino(titular);
			titular.corregirContactoDelTitular(telefono, nuevoCorreo);
			usuarios.saveAndFlush(titular);
			enlaces.anularVigentes(titular.getId());
			auditoria.registrar(AccionAuditoria.TITULAR_CONTACTO_CORREGIDO, "usuario", titular.getId().toString(), anterior,
					destino(titular), "El operador " + operador.nombreUsuario() + " corrigió el contacto del titular "
							+ titular.getNombreUsuario() + " antes de la activación y le reenvió su enlace. El contacto anterior "
							+ "(" + anterior + ") ya no recibe nada. Si el titular no lo pidió, es un incidente.");
			eventos.publishEvent(new EnvioEnlaceSolicitado(titular.getId(), PropositoEnlace.PERSONAL, null));
			return new TitularCreado(titular.getId(), titular.getNombreUsuario(), canal(titular), destino(titular));
		}));
	}

	/** El titular (la cuenta de la primera Promotoría del colegio en contexto), todavía sin activar. */
	public Usuario titularSinActivar() {
		Usuario titular = primeras.findFirstByIdNotNull().flatMap(p -> usuarios.findById(p.getUsuarioId()))
				.orElseThrow(() -> new ReglaNegocioException("Este colegio todavía no tiene su primera Promotoría: completa el alta."));
		if (!titular.isDebeCambiarClave()) {
			throw new ReglaNegocioException("El titular ya activó su cuenta: su contacto lo cambia él desde «Mi celular y "
					+ "correo», con la aprobación de otra persona.");
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
		return usuario.getTelefonoWhatsapp() != null ? "WhatsApp " + Enmascarar.telefono(usuario.getTelefonoWhatsapp())
				: "correo " + Enmascarar.correo(usuario.getCorreo());
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj);
	}
}
