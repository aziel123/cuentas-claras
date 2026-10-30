package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.BandejaVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.TipoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.SegundaFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad.EjecucionIdentidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Bandeja de aprobaciones (Promotoría y Dirección): aprueba o rechaza las solicitudes. Quien la pidió (o quien creó o
 * restableció la clave de esa cuenta en los últimos 30 días) no la resuelve: el intento queda auditado. Al aprobar,
 * la solicitud queda APROBADA (con flush) y después el manejador del tipo aplica el cambio en la misma transacción: si
 * falla, nada queda aprobado (M1: los triggers de MySQL exigen la solicitud aprobada para reabrir una caja o anular una
 * cuota). Si el manejador pide llamadas (A2), se exige «Hablé con el apoderado» y los números llamados.
 * <p>
 * Sprint 7, tanda 2:
 * <ul>
 *   <li><b>Firma de sesión</b> (sección 3.4): antes de resolver, quien aprueba o rechaza firma la clave
 *       {@code solicitud_cambio:id:estado} con el secreto de su sesión ({@link FirmaSesion}); en MySQL,
 *       trg_solicitud_cambio_resuelta lo exige. Con la clave de {@code cc_app} no se aprueba a nombre de otra persona.</li>
 *   <li><b>Tipos de identidad</b> ({@link #TIPOS_DE_IDENTIDAD}: contacto y roles del personal): la resolución, su firma
 *       y el cambio de la cuenta corren completos en la ruta de identidad ({@code cc_sistema}), en una sola transacción.
 *       Los demás, en la de la aplicación.</li>
 * </ul>
 * Sprint 11, tanda 1c (secciones 3.16 y 3.16b, decisiones 215 a 225): la Promotoría no pide aprobación a personas de menor
 * jerarquía ({@link SegundaFirma}):
 * <ul>
 *   <li><b>Directa:</b> lo que pide una Promotoría lo resuelve ella misma («Aprobar y aplicar»), con su firma; si es
 *       sensible ({@link TipoSolicitud#sensible()}), con su código por WhatsApp atado a la acción
 *       ({@link CodigoPromotoria}). Lo no sensible se aplica solo al pedirlo ({@link RegistroSolicitudes}). Cada directa
 *       avisa en la misma transacción a todas las Promotorías (también a quien la hizo) y al correo de control; con una
 *       sola Promotoría, lo que anula o devuelve dinero avisa además a los operadores (A1c-9).</li>
 *   <li><b>Espera</b> ({@link EsperaDirecta}): dar Promotoría en producción, quitarla y desactivar a una Promotoría
 *       esperan 24 horas; los frena otra Promotoría con 30 días en el rol, la persona afectada o un operador; al vencer
 *       los aplica {@code sistema.roles}.</li>
 *   <li><b>A5</b> deja de bloquear a la Promotoría (aprueba lo de las cuentas que creó) y sigue para los demás.</li>
 *   <li><b>El contacto de una cuenta directiva</b> (A1c-1) nunca es directo: se aprueba solo cuando su dueña lo confirmó
 *       desde su contacto anterior (o lo aplican dos operadores).</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
public class BandejaAprobaciones {

	private static final Logger LOG = LoggerFactory.getLogger(BandejaAprobaciones.class);

	private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	/** Sprint 7, tanda 2: se resuelven por la ruta de identidad (escriben usuario o usuario_rol). */
	public static final Set<TipoSolicitud> TIPOS_DE_IDENTIDAD = EnumSet.of(TipoSolicitud.CAMBIO_CONTACTO_PERSONAL,
			TipoSolicitud.CAMBIO_ROLES, TipoSolicitud.ESTADO_CUENTA);

	private final SolicitudCambioRepository solicitudes;

	/** Los manejadores exigen una transacción abierta (también {@code tipo()}): se eligen dentro de ella. */
	private final List<ManejadorSolicitud> manejadores;

	/** Correcciones del sprint 8 (QA-S8-6): comprobaciones antes de abrir la transacción de la aprobación. */
	private List<ValidacionPrevia> previas = List.of();

	private final SegundaFirma segundaFirma;

	private final CodigoPromotoria codigos;

	private final EsperaDirecta espera;

	private final UsuarioRepository usuarios;

	private final ColegioRepository colegios;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	private final FirmaSesion firma;

	private final EjecucionIdentidad identidad;

	private final TransactionTemplate transaccion;

	/** Tanda 1c: los avisos a los operadores de la resolución en curso (se envían después, en el colegio de plataforma). */
	private final ThreadLocal<List<AvisoPendiente>> avisosPendientes = ThreadLocal.withInitial(ArrayList::new);

	/** Un aviso a los operadores que sale después de confirmar (es otro colegio: otra transacción). */
	private record AvisoPendiente(Long colegioId, AvisoPlataforma.Tipo tipo, List<String> parametros, Long solicitudId,
			String detalle) {
	}

	/** Las comprobaciones previas de cada tipo (opcionales: las aporta el módulo dueño del dato). */
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	void setPrevias(List<ValidacionPrevia> previas) {
		this.previas = previas == null ? List.of() : List.copyOf(previas);
	}

	public BandejaAprobaciones(SolicitudCambioRepository solicitudes, List<ManejadorSolicitud> manejadores,
			SegundaFirma segundaFirma, CodigoPromotoria codigos, EsperaDirecta espera, UsuarioRepository usuarios,
			ColegioRepository colegios, AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock reloj,
			FirmaSesion firma, EjecucionIdentidad identidad, PlatformTransactionManager transacciones) {
		this.firma = firma;
		this.identidad = identidad;
		this.transaccion = new TransactionTemplate(transacciones);
		this.solicitudes = solicitudes;
		this.manejadores = List.copyOf(manejadores);
		this.segundaFirma = segundaFirma;
		this.codigos = codigos;
		this.espera = espera;
		this.usuarios = usuarios;
		this.colegios = colegios;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/**
	 * Pendientes por prioridad del manejador: primero los cierres de caja con diferencia, luego las anulaciones de pago
	 * (mueven dinero ya cobrado) y después el resto, en orden de llegada.
	 */
	@Transactional(readOnly = true)
	public BandejaVista bandeja() {
		String usuario = usuario();
		return new BandejaVista(
				solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
						.sorted(Comparator.comparingInt((SolicitudCambio s) -> manejadorDe(s).prioridad(s)))
						.map(s -> vista(s, usuario, true)).toList(),
				solicitudes.findTop30ByEstadoNotOrderByResueltoEnDescIdDesc(EstadoSolicitud.PENDIENTE).stream()
						.map(s -> vista(s, usuario, false)).toList(),
				solicitudes.findByTipoOrderByIdDesc(TipoSolicitud.FECHA_MATRICULA).stream()
						.map(s -> vista(s, usuario, false)).toList());
	}

	/**
	 * Sprint 6, tanda 2 (decisión 72): una solicitud para la vista del celular, con su detalle y si quien está en sesión
	 * puede resolverla (la misma regla que la bandeja: quien la pidió o participó, no). La de otro colegio no existe (404).
	 */
	@Transactional(readOnly = true)
	public SolicitudVista detalle(Long id) {
		SolicitudCambio solicitud = solicitudes.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
		return vista(solicitud, usuario(), true);
	}

	/** Sprint 6, tanda 2: cuántas solicitudes esperan aprobación (el resumen diario las informa; lo lee sistema.panel). */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','SISTEMA_PANEL')")
	@Transactional(readOnly = true)
	public long contarPendientes() {
		return solicitudes.countByEstado(EstadoSolicitud.PENDIENTE);
	}

	/**
	 * Rediseño de la interfaz (auditoría del PR #19, hallazgo 2): el contador del menú. Solo las pendientes que quien está
	 * en sesión puede resolver: la misma regla que {@code puedeResolver} de la bandeja (no las que pidió ni en las que
	 * participó). Mismos roles que el módulo Aprobaciones.
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	@Transactional(readOnly = true)
	public long contarPorResolver() {
		String usuario = usuario();
		return solicitudes.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE).stream()
				.filter(s -> puedeResolver(s, usuario)).count();
	}

	/** Aprueba una solicitud que no pide llamadas. */
	public void aprobar(Long id, String comentario) {
		aprobar(id, comentario, false, List.of());
	}

	/**
	 * Aprueba: {@code hablo} y {@code telefonos} son la confirmación de las llamadas que pide el manejador (A2): un
	 * número por cada familia, que debe ser un celular registrado de un apoderado de esa familia.
	 */
	public void aprobar(Long id, String comentario, boolean hablo, List<String> telefonos) {
		aprobar(id, comentario, hablo, telefonos, null);
	}

	/**
	 * Sprint 11, tanda 1c: aprueba con el código de la Promotoría ({@code codigo}) cuando es la directa de una acción
	 * sensible que ella misma pidió («Aprobar y aplicar»).
	 */
	public void aprobar(Long id, String comentario, boolean hablo, List<String> telefonos, CodigoPromotoria.Escrito codigo) {
		validarAntes(id);
		try {
			resolverEnSuRuta(id, () -> aprobarEnTransaccion(id, comentario, hablo, telefonos, codigo));
		}
		catch (RuntimeException e) {
			avisosPendientes.get().clear();
			throw e;
		}
		enviarAvisosAOperadores();
	}

	/**
	 * Sprint 11, tanda 1c: lo que necesita la pantalla para pedir el código de una directa sensible que pidió quien está en
	 * sesión: la acción (su huella y su descripción).
	 */
	@Transactional(readOnly = true)
	public CodigoPromotoria.Accion accionDelCodigo(Long id) {
		SolicitudCambio s = solicitudes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
		if (!s.estaPendiente() || !Objects.equals(s.getSolicitadoPor(), usuario()) || !s.getTipo().sensible()) {
			throw new ReglaNegocioException("Esta solicitud no pide tu código.");
		}
		return new CodigoPromotoria.Accion(s.huellaAccion(), s.getTipo().etiqueta() + ": " + s.getResumen());
	}

	/** Sprint 11, tanda 1c: «Enviar código» de la directa sensible que pidió quien está en sesión (sin transacción abierta). */
	public CodigoPromotoria.Pedido pedirCodigo(Long id) {
		CodigoPromotoria.Accion accion = transaccion.execute(t -> accionDelCodigo(id));
		return codigos.pedir(accion);
	}

	/** Solo en dev y en la demo: el código del pedido, para mostrarlo en la pantalla. */
	public java.util.Optional<String> codigoDeDemostracion(CodigoPromotoria.Pedido pedido) {
		return pedido == null ? java.util.Optional.empty() : codigos.codigoDeDemostracion(pedido.mensajeId());
	}

	/**
	 * Sprint 11, tanda 1c (RegistroSolicitudes): la directa de una acción NO sensible que acaba de pedir una Promotoría, al
	 * final de la misma transacción en que la pidió. Si el manejador pide llamadas (A2), queda pendiente para «Aprobar y
	 * aplicar» con la confirmación.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void aplicarDirectaAlPedir(Long id) {
		SolicitudCambio solicitud = solicitudes.findById(id).orElse(null);
		if (solicitud == null || !solicitud.estaPendiente() || solicitud.getTipo().sensible()
				|| !directaPermitida(solicitud, usuario())) {
			return;
		}
		ManejadorSolicitud manejador = manejadorDe(solicitud);
		if (!manejador.llamadas(solicitud).isEmpty()) {
			return;
		}
		aplicarDirecta(solicitud, manejador, null, null);
	}

	/**
	 * Sprint 11, tanda 1c (A1c-2 y A1c-3): las directas con espera que todavía no se aplicaron (para {@code sistema.roles}).
	 */
	@PreAuthorize("hasRole('SISTEMA_ROLES')")
	@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
	public List<Long> enEsperaVencidas() {
		LocalDateTime ahora = ahora();
		return solicitudes.findByEstadoAndDirectaTrueAndFrenadoPorIsNullAndTipoInOrderByIdAsc(EstadoSolicitud.APROBADA,
				EnumSet.of(TipoSolicitud.CAMBIO_ROLES, TipoSolicitud.ESTADO_CUENTA)).stream()
				.filter(this::esperaPendiente)
				.filter(s -> {
					LocalDateTime vence = espera.vence(s, true);
					return vence != null && !vence.isAfter(ahora);
				})
				.map(SolicitudCambio::getId).toList();
	}

	/**
	 * Sprint 11, tanda 1c (A1c-2 y A1c-3): {@code sistema.roles} aplica una directa cuya espera venció sin freno (en la ruta
	 * de cc_sistema: trg_usuario_rol_* y trg_usuario_identidad lo exigen con cc_solicitud_firmada). Avisa a todos.
	 */
	@PreAuthorize("hasRole('SISTEMA_ROLES')")
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean aplicarAlVencer(Long id) {
		SolicitudCambio solicitud = solicitudes.bloquear(id).orElse(null);
		if (solicitud == null || solicitud.getEstado() != EstadoSolicitud.APROBADA || !solicitud.isDirecta()
				|| solicitud.estaFrenada() || !esperaPendiente(solicitud)) {
			return false;
		}
		LocalDateTime vence = espera.vence(solicitud, true);
		if (vence == null || vence.isAfter(ahora())) {
			return false;
		}
		manejadorDe(solicitud).aplicar(solicitud, solicitud.getResueltoPor(), null);
		String que = solicitud.getTipo().etiqueta() + ": " + solicitud.getResumen();
		auditoria.registrar(AccionAuditoria.DIRECTA_APLICADA_AL_VENCER, "solicitud_cambio", id.toString(), "en espera",
				"aplicada", que + ". Terminó la espera de 24 horas sin freno; la pidió y la aprobó directo "
						+ solicitud.getResueltoPor() + ".");
		String nombre = usuarios.findByNombreUsuario(solicitud.getResueltoPor()).map(Usuario::getNombreCompleto)
				.orElse(solicitud.getResueltoPor());
		eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.ACCION_DIRECTA,
				List.of(nombre, que + " (terminó su espera)"), "solicitud_cambio", id, "V",
				"usuario".equals(solicitud.getEntidad()) ? solicitud.getEntidadId() : null, false, true, true));
		return true;
	}

	/**
	 * Correcciones del sprint 8 (QA-S8-6): las {@link ValidacionPrevia} del tipo, ANTES de la transacción (necesitan ver
	 * otros colegios). Si quien llama ya tiene una transacción abierta, no se pueden correr aquí: valen las del manejador y
	 * las de MySQL.
	 */
	private void validarAntes(Long id) {
		if (previas.isEmpty() || org.springframework.transaction.support.TransactionSynchronizationManager
				.isActualTransactionActive()) {
			return;
		}
		SolicitudCambio solicitud = transaccion.execute(t -> solicitudes.findById(id).orElse(null));
		if (solicitud == null || solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
			return;
		}
		previas.stream().filter(v -> v.tipo() == solicitud.getTipo()).forEach(v -> v.validar(solicitud));
	}

	private void aprobarEnTransaccion(Long id, String comentario, boolean hablo, List<String> telefonos,
			CodigoPromotoria.Escrito codigo) {
		SolicitudCambio solicitud = pendiente(id);
		String usuario = usuario();
		ManejadorSolicitud manejador = manejadorDe(solicitud);
		if (Objects.equals(usuario, solicitud.getSolicitadoPor())) {
			if (!directaPermitida(solicitud, usuario)) {
				exigirOtraPersona(solicitud, usuario, "aprobar");
			}
			// Tanda 1c: la directa de la Promotoría que la pidió. Lo sensible, con su código atado a esta acción.
			CodigoPromotoria.Valido valido = solicitud.getTipo().sensible()
					? codigos.exigir(codigo, solicitud.huellaAccion()) : null;
			String llamadas = confirmarLlamadas(manejador, solicitud, hablo, telefonos);
			aplicarDirecta(solicitud, manejador, valido, llamadas);
			return;
		}
		exigirOtraPersona(solicitud, usuario, "aprobar");
		exigirConfirmacionDelContacto(solicitud);
		String llamadas = confirmarLlamadas(manejador, solicitud, hablo, telefonos);
		firma.firmar(ClaveFirma.solicitud(id, EstadoSolicitud.APROBADA));
		solicitud.aprobar(usuario, comentario, ahora());
		solicitudes.saveAndFlush(solicitud);
		manejador.aplicar(solicitud, usuario, comentario);
		auditoria.registrar(AccionAuditoria.SOLICITUD_APROBADA, "solicitud_cambio", id.toString(),
				EstadoSolicitud.PENDIENTE.name(), EstadoSolicitud.APROBADA.name(), solicitud.getTipo().etiqueta() + ": "
						+ solicitud.getResumen() + ". Pedida por " + solicitud.getSolicitadoPor() + "."
						+ (llamadas == null ? "" : " " + llamadas));
	}

	/**
	 * Tanda 1c: la Promotoría resuelve directo lo que pidió (su firma ya va con la clave {@code :APROBADA}). Si el tipo
	 * espera 24 horas, el manejador no corre ahora: lo aplica {@code sistema.roles} al vencer, si nadie lo frena.
	 */
	private void aplicarDirecta(SolicitudCambio solicitud, ManejadorSolicitud manejador, CodigoPromotoria.Valido codigo,
			String llamadas) {
		String usuario = usuario();
		boolean espera = this.espera.requiere(solicitud);
		firma.firmar(ClaveFirma.solicitud(solicitud.getId(), EstadoSolicitud.APROBADA));
		solicitud.aprobarDirecta(usuario, codigo == null ? null : codigo.mensajeId(), codigo == null ? null : codigo.sha256(),
				ahora());
		solicitudes.saveAndFlush(solicitud);
		String que = solicitud.getTipo().etiqueta() + ": " + solicitud.getResumen();
		String nombre = usuarios.findByNombreUsuario(usuario).map(Usuario::getNombreCompleto).orElse(usuario);
		auditoria.registrar(AccionAuditoria.SOLICITUD_APROBADA, "solicitud_cambio", solicitud.getId().toString(),
				EstadoSolicitud.PENDIENTE.name(), EstadoSolicitud.APROBADA.name() + " (aplicada directo por Promotoría)",
				que + ". La pidió y la aprobó " + usuario + (codigo == null ? "" : ", con su código por WhatsApp") + "."
						+ (llamadas == null ? "" : " " + llamadas));
		Long afectado = "usuario".equals(solicitud.getEntidad()) ? solicitud.getEntidadId() : null;
		if (espera) {
			LocalDateTime vence = Objects.requireNonNullElse(solicitudes.aplicaDesdeDe(solicitud.getId()),
					solicitud.getResueltoEn().plus(EsperaDirecta.ESPERA));
			auditoria.registrar(AccionAuditoria.DIRECTA_EN_ESPERA, "solicitud_cambio", solicitud.getId().toString(), null,
					"se aplica el " + vence.format(FECHA_HORA), que + ". Espera 24 horas: la puede frenar otra Promotoría "
							+ "con 30 días en el rol, la persona afectada o un operador. Se avisó a todas las Promotorías, al "
							+ "correo de control, a la persona afectada y a los operadores.");
			List<String> parametros = List.of(nombre, que, vence.format(FECHA_HORA));
			eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.DIRECTA_EN_ESPERA,
					parametros, "solicitud_cambio", solicitud.getId(), null, afectado, false, true, true));
			avisosPendientes.get().add(new AvisoPendiente(ContextoColegio.actual(), AvisoPlataforma.Tipo.DIRECTA_EN_ESPERA,
					parametros, solicitud.getId(), que + " (espera 24 horas)."));
			return;
		}
		manejador.aplicar(solicitud, usuario, null);
		auditoria.registrar(AccionAuditoria.ACCION_DIRECTA_PROMOTORIA, "solicitud_cambio", solicitud.getId().toString(),
				null, que, "Aplicada directo por " + usuario + ", sin segunda aprobación" + (codigo == null ? ""
						: ", con su firma y su código por WhatsApp") + ". Se avisó a todas las Promotorías y al correo de "
						+ "control.");
		// Decisión 218: el conector que mueve dinero no entra todavía: espera 24 horas (y lo pueden frenar los operadores).
		boolean conectorEnEspera = manejador.avisaOperadoresAlAplicarDirecta(solicitud);
		AvisoPlataforma.Tipo tipoAviso = conectorEnEspera ? AvisoPlataforma.Tipo.DIRECTA_EN_ESPERA
				: AvisoPlataforma.Tipo.ACCION_DIRECTA;
		List<String> parametros = conectorEnEspera ? List.of(nombre, que, ahora().plus(EsperaDirecta.ESPERA)
				.format(FECHA_HORA)) : List.of(nombre, que);
		eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), tipoAviso, parametros, "solicitud_cambio",
				solicitud.getId(), null, afectado, false, true, true));
		// A1c-9: con una sola Promotoría, lo que anula o devuelve dinero lo ven también los operadores.
		if ((solicitud.getTipo().anulaODevuelveDinero() && segundaFirma.promotoriasActivas() <= 1) || conectorEnEspera) {
			avisosPendientes.get().add(new AvisoPendiente(ContextoColegio.actual(), tipoAviso, parametros, solicitud.getId(),
					que + (conectorEnEspera ? " (espera 24 horas)." : " (colegio con una sola Promotoría).")));
		}
	}

	private static String confirmarLlamadas(ManejadorSolicitud manejador, SolicitudCambio solicitud, boolean hablo,
			List<String> telefonos) {
		List<String> llamadas = manejador.llamadas(solicitud);
		if (llamadas.isEmpty()) {
			return null;
		}
		List<String> numeros = telefonos == null ? List.of()
				: telefonos.stream().filter(t -> t != null && !t.isBlank()).toList();
		if (!hablo || numeros.size() != llamadas.size()) {
			throw new ReglaNegocioException(llamadas.size() == 1
					? "Antes de aprobar, llama al apoderado: marca «Hablé con el apoderado» y escribe el número al que llamaste."
					: "Antes de aprobar, llama a ambas familias: marca «Hablé con ambas familias» y escribe los dos números.");
		}
		return manejador.confirmarLlamadas(solicitud, numeros);
	}

	public void rechazar(Long id, String motivo) {
		resolverEnSuRuta(id, () -> rechazarEnTransaccion(id, motivo));
	}

	private void rechazarEnTransaccion(Long id, String motivo) {
		SolicitudCambio solicitud = pendiente(id);
		String usuario = usuario();
		// Quien la pidió no la rechaza (ni la Promotoría que la aplicaría directo): el intento queda auditado.
		if (Objects.equals(usuario, solicitud.getSolicitadoPor())) {
			rechazarAutoaprobacion(solicitud, "rechazar");
		}
		exigirOtraPersona(solicitud, usuario, "rechazar");
		firma.firmar(ClaveFirma.solicitud(id, EstadoSolicitud.RECHAZADA));
		solicitud.rechazar(usuario, motivo, ahora());
		manejadorDe(solicitud).alRechazar(solicitud);
		solicitudes.saveAndFlush(solicitud);
		auditoria.registrar(AccionAuditoria.SOLICITUD_RECHAZADA, "solicitud_cambio", id.toString(),
				EstadoSolicitud.PENDIENTE.name(), EstadoSolicitud.RECHAZADA.name(), solicitud.getTipo().etiqueta() + ": "
						+ solicitud.getResumen() + ". Motivo del rechazo: " + solicitud.getComentario());
	}

	/**
	 * Sprint 11, tanda 1c (A1c-2 y A1c-3): frena una directa que espera. Lo hace otra Promotoría con 30 días en el rol o
	 * la persona afectada, con su firma y un motivo; queda en la bitácora y se avisa a todos. El operador frena desde la
	 * plataforma.
	 */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION','CAJA','DOCENTE')")
	public void frenar(Long id, String motivo) {
		resolverEnSuRuta(id, () -> {
			SolicitudCambio solicitud = solicitudes.bloquear(id)
					.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
			String usuario = usuario();
			boolean afectado = "usuario".equals(solicitud.getEntidad()) && usuarios.findById(solicitud.getEntidadId())
					.map(u -> u.getNombreUsuario().equals(usuario)).orElse(false);
			if (Objects.equals(usuario, solicitud.getResueltoPor())) {
				throw new ReglaNegocioException("Tú lo pediste: no lo frenas tú (si no fuiste tú, cambia tu clave y avisa al "
						+ "proveedor). Lo frena otra Promotoría, la persona afectada o un operador.");
			}
			if (!afectado && !segundaFirma.promotoriaAntigua(usuario)) {
				throw new ReglaNegocioException("Frena una espera otra Promotoría con 30 días o más en el rol, la persona "
						+ "afectada o un operador de la plataforma.");
			}
			LocalDateTime vence = espera.vence(solicitud, true);
			firma.firmar(ClaveFirma.solicitudFrenada(id));
			solicitud.frenar(usuario, motivo, ahora(), vence);
			solicitudes.saveAndFlush(solicitud);
			String que = solicitud.getTipo().etiqueta() + ": " + solicitud.getResumen();
			auditoria.registrar(AccionAuditoria.DIRECTA_FRENADA, "solicitud_cambio", id.toString(), "en espera", "frenada",
					que + ". La frenó " + usuario + ": " + solicitud.getMotivoFreno() + ". No se aplica.");
			String nombre = usuarios.findByNombreUsuario(usuario).map(Usuario::getNombreCompleto).orElse(usuario);
			eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.DIRECTA_FRENADA,
					List.of(nombre, que, solicitud.getMotivoFreno()), "solicitud_cambio", id, null,
					"usuario".equals(solicitud.getEntidad()) ? solicitud.getEntidadId() : null, false, true, true));
		});
	}

	/**
	 * Sprint 11, tanda 1c (A1c-2 y A1c-3): las directas que esperan, del colegio en contexto, para la pantalla del operador
	 * (que las puede frenar). Lo lee el operador en la transacción de cc_sistema que abrió la plataforma.
	 */
	@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
	@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
	public List<pe.edu.virgenmaria.cuentasclaras.colegio.service.EsperaVista> enEsperaParaOperador() {
		LocalDateTime ahora = ahora();
		return solicitudes.findByEstadoAndDirectaTrueAndFrenadoPorIsNullAndTipoInOrderByIdAsc(EstadoSolicitud.APROBADA,
				EnumSet.of(TipoSolicitud.CAMBIO_ROLES, TipoSolicitud.ESTADO_CUENTA)).stream()
				.filter(this::esperaPendiente)
				.map(s -> new pe.edu.virgenmaria.cuentasclaras.colegio.service.EsperaVista("SOLICITUD", s.getId(),
						s.getTipo().etiqueta() + ": " + s.getResumen(), s.getResueltoPor(), espera.vence(s, true)))
				.filter(e -> e.vence() != null && e.vence().isAfter(ahora)).toList();
	}

	/**
	 * Sprint 11, tanda 1c (A1c-2 y A1c-3): el operador de la plataforma frena una directa que espera (con su firma ya dada
	 * en el colegio de plataforma), en la transacción de cc_sistema que abrió la plataforma con el colegio en contexto.
	 */
	@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
	@Transactional(propagation = Propagation.MANDATORY)
	public String frenarComoOperador(Long id, String motivo) {
		SolicitudCambio solicitud = solicitudes.bloquear(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
		if (!esperaPendiente(solicitud)) {
			throw new ReglaNegocioException("Esta solicitud no está en espera.");
		}
		String operador = usuario();
		solicitud.frenar(operador, motivo, ahora(), espera.vence(solicitud, true));
		solicitudes.saveAndFlush(solicitud);
		String que = solicitud.getTipo().etiqueta() + ": " + solicitud.getResumen();
		auditoria.registrar(AccionAuditoria.DIRECTA_FRENADA, "solicitud_cambio", id.toString(), "en espera", "frenada",
				que + ". La frenó el operador " + operador + " de la plataforma: " + solicitud.getMotivoFreno()
						+ ". No se aplica.");
		eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.DIRECTA_FRENADA,
				List.of("El operador " + operador + " de la plataforma", que, solicitud.getMotivoFreno()), "solicitud_cambio",
				id, null, "usuario".equals(solicitud.getEntidad()) ? solicitud.getEntidadId() : null, false, true, true));
		return que;
	}

	/**
	 * Resuelve en una sola transacción: la de identidad ({@code cc_sistema}) para {@link #TIPOS_DE_IDENTIDAD}; para los
	 * demás, la de la aplicación (si quien llama ya tiene una, se une). Un intento de resolver lo propio
	 * ({@link AutoaprobacionSolicitudException}) NO revierte: su bitácora se confirma y después se relanza.
	 */
	private void resolverEnSuRuta(Long id, Runnable resolucion) {
		TipoSolicitud tipo = transaccion.execute(t -> solicitudes.findById(id).map(SolicitudCambio::getTipo).orElse(null));
		if (tipo != null && TIPOS_DE_IDENTIDAD.contains(tipo)) {
			identidad.como(() -> {
				resolucion.run();
				return null;
			}, List.of(AutoaprobacionSolicitudException.class));
			return;
		}
		AutoaprobacionSolicitudException[] diferida = new AutoaprobacionSolicitudException[1];
		transaccion.executeWithoutResult(t -> {
			try {
				resolucion.run();
			}
			catch (AutoaprobacionSolicitudException e) {
				diferida[0] = e;
			}
		});
		if (diferida[0] != null) {
			throw diferida[0];
		}
	}

	/**
	 * Tanda 1c: después de confirmar, en el colegio de plataforma (otra transacción), el aviso a los operadores y su evento.
	 * Si falla, queda en el log: el testigo del colegio (Promotorías y correo de control) ya recibió el suyo.
	 */
	private void enviarAvisosAOperadores() {
		List<AvisoPendiente> pendientes = List.copyOf(avisosPendientes.get());
		avisosPendientes.get().clear();
		if (pendientes.isEmpty() || org.springframework.transaction.support.TransactionSynchronizationManager
				.isActualTransactionActive()) {
			return;
		}
		for (AvisoPendiente aviso : pendientes) {
			try {
				Long plataforma = transaccion.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA)
						.map(Colegio::getId).orElse(null));
				String colegio = transaccion.execute(t -> colegios.findById(aviso.colegioId()).map(Colegio::getNombre)
						.orElse("?"));
				if (plataforma == null) {
					continue;
				}
				List<String> parametros = new ArrayList<>(aviso.parametros());
				parametros.set(0, parametros.getFirst() + " (" + colegio + ")");
				ContextoColegio.en(plataforma, () -> identidad.ejecutar(() -> {
					eventos.publishEvent(new AvisoOperadores(plataforma, aviso.tipo(), parametros, "colegio",
							aviso.colegioId(), "S" + aviso.solicitudId()));
					auditoria.registrar(aviso.tipo() == AvisoPlataforma.Tipo.DIRECTA_EN_ESPERA
							? AccionAuditoria.DIRECTA_EN_ESPERA : AccionAuditoria.ACCION_DIRECTA_PROMOTORIA, "colegio",
							aviso.colegioId().toString(), null, null, "Colegio " + colegio + ": " + aviso.detalle());
				}));
			}
			catch (RuntimeException e) {
				LOG.warn("No se pudo avisar a los operadores de la solicitud {} del colegio {}: {}", aviso.solicitudId(),
						aviso.colegioId(), e.getClass().getSimpleName());
			}
		}
	}

	/** El manejador del tipo (y, si el tipo lo comparten varios módulos, el de la entidad de la solicitud). */
	private ManejadorSolicitud manejadorDe(SolicitudCambio solicitud) {
		List<ManejadorSolicitud> delTipo = manejadores.stream().filter(m -> m.tipo() == solicitud.getTipo())
				.filter(m -> m.entidad() == null || m.entidad().equals(solicitud.getEntidad())).toList();
		if (delTipo.size() != 1) {
			throw new IllegalStateException(delTipo.size() + " manejadores para " + solicitud.getTipo() + " de "
					+ solicitud.getEntidad());
		}
		return delTipo.getFirst();
	}

	private SolicitudCambio pendiente(Long id) {
		SolicitudCambio solicitud = solicitudes.bloquear(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
		if (!solicitud.estaPendiente()) {
			throw new ReglaNegocioException("La solicitud ya fue " + solicitud.getEstado().etiqueta().toLowerCase()
					+ " por " + solicitud.getResueltoPor() + ".");
		}
		return solicitud;
	}

	/** Quien la pidió y los involucrados que dice su manejador (la cajera del pago), más quienes prepararon esas cuentas. */
	private Set<String> participantesDe(SolicitudCambio solicitud) {
		Set<String> autores = new LinkedHashSet<>();
		autores.add(solicitud.getSolicitadoPor());
		autores.addAll(manejadorDe(solicitud).involucrados(solicitud));
		return segundaFirma.participantes(autores);
	}

	/**
	 * Tanda 1c: si {@code usuario} puede resolver esta pendiente. La que pidió: solo una Promotoría, como directa. La de
	 * otra persona: una Promotoría, salvo que sea parte (la cuenta afectada, la cajera del pago); los demás, con A5.
	 */
	private boolean puedeResolver(SolicitudCambio solicitud, String usuario) {
		if (Objects.equals(usuario, solicitud.getSolicitadoPor())) {
			return directaPermitida(solicitud, usuario);
		}
		if (segundaFirma.aplicaDirecto(usuario)) {
			return !manejadorDe(solicitud).involucrados(solicitud).contains(usuario);
		}
		return !participantesDe(solicitud).contains(usuario);
	}

	/**
	 * Tanda 1c: si la solicitud la puede resolver directo quien la pidió: una Promotoría activa en un colegio que opera,
	 * nunca sobre el contacto propio ni el de una cuenta directiva (P1 y A1c-1).
	 */
	private boolean directaPermitida(SolicitudCambio solicitud, String usuario) {
		if (!segundaFirma.aplicaDirecto(usuario)) {
			return false;
		}
		return solicitud.getTipo() != TipoSolicitud.CAMBIO_CONTACTO_PERSONAL || !cuentaDirectivaOPropia(solicitud, usuario);
	}

	private boolean cuentaDirectivaOPropia(SolicitudCambio solicitud, String usuario) {
		return usuarios.findById(solicitud.getEntidadId()).map(u -> u.getNombreUsuario().equals(usuario)
				|| u.getRoles().contains(Rol.PROMOTOR) || u.getRoles().contains(Rol.DIRECTOR)).orElse(true);
	}

	/** A1c-1: el contacto de una cuenta directiva se aprueba solo después de que su dueña lo confirmó desde el anterior. */
	private void exigirConfirmacionDelContacto(SolicitudCambio solicitud) {
		if (solicitud.getTipo() == TipoSolicitud.CAMBIO_CONTACTO_PERSONAL
				&& cuentaDirectivaOPropia(solicitud, solicitud.getSolicitadoPor())
				&& usuarios.findById(solicitud.getEntidadId()).map(u -> u.getRoles().contains(Rol.PROMOTOR)
						|| u.getRoles().contains(Rol.DIRECTOR)).orElse(false)
				&& !solicitud.confirmadaDesdeContactoAnterior()) {
			throw new ReglaNegocioException("El contacto de una cuenta de Promotoría o Dirección cambia solo cuando su dueña "
					+ "lo confirma desde su celular o correo ANTERIOR (con el código que le llega ahí), o con dos operadores "
					+ "de la plataforma si lo perdió. Todavía no lo confirmó.");
		}
	}

	private void exigirOtraPersona(SolicitudCambio solicitud, String usuario, String accion) {
		if (!puedeResolver(solicitud, usuario)) {
			rechazarAutoaprobacion(solicitud, accion);
		}
	}

	private void rechazarAutoaprobacion(SolicitudCambio solicitud, String accion) {
		{
			auditoria.registrar(AccionAuditoria.AUTOAPROBACION_RECHAZADA, "solicitud_cambio",
					solicitud.getId().toString(), null, solicitud.getTipo().etiqueta() + ": " + solicitud.getResumen(),
					"Intentó " + accion + " una solicitud que pidió o en la que participó (o una cuenta que preparó). Se rechazó.");
			throw new AutoaprobacionSolicitudException("No puedes " + accion + " una solicitud que tú pediste o en la que "
					+ "participaste (por ejemplo, el pago que cobraste), ni la de una cuenta que creaste o a la que le "
					+ "restableciste la clave: debe hacerlo otra persona.");
		}
	}

	private SolicitudVista vista(SolicitudCambio s, String usuario, boolean conDetalle) {
		boolean puede = s.estaPendiente() && puedeResolver(s, usuario);
		ManejadorSolicitud manejador = conDetalle ? manejadorDe(s) : null;
		boolean propia = Objects.equals(usuario, s.getSolicitadoPor());
		boolean requiereEspera = s.isDirecta() && !s.estaFrenada() && esperaPendiente(s);
		LocalDateTime vence = requiereEspera ? espera.vence(s, true) : null;
		boolean enEspera = vence != null && vence.isAfter(ahora());
		boolean puedeFrenar = enEspera && !Objects.equals(usuario, s.getResueltoPor())
				&& (segundaFirma.promotoriaAntigua(usuario) || ("usuario".equals(s.getEntidad())
						&& usuarios.findById(s.getEntidadId()).map(u -> u.getNombreUsuario().equals(usuario)).orElse(false)));
		String advertencia = manejador == null ? null : manejador.advertencia(s);
		if (conDetalle && s.estaPendiente() && s.getTipo() == TipoSolicitud.CAMBIO_CONTACTO_PERSONAL
				&& usuarios.findById(s.getEntidadId()).map(u -> u.getRoles().contains(Rol.PROMOTOR)
						|| u.getRoles().contains(Rol.DIRECTOR)).orElse(false)) {
			advertencia = s.confirmadaDesdeContactoAnterior()
					? "Su dueña ya confirmó este cambio desde su contacto anterior."
					: "Es el contacto de una cuenta de Promotoría o Dirección: se aprueba solo cuando su dueña lo confirma "
							+ "desde su contacto ANTERIOR (o con dos operadores de la plataforma).";
		}
		return new SolicitudVista(s.getId(), s.getTipo().name(), s.getTipo().etiqueta(), s.getResumen(), s.getMotivo(),
				s.getEstado().name(), s.getEstado().etiqueta(), s.getEstado().variante(), s.getSolicitadoPor(),
				s.getCreadoEn(), s.getResueltoPor(), s.getResueltoEn(), s.getComentario(), puede,
				manejador == null ? List.of() : manejador.detalle(s), advertencia,
				manejador != null && manejador.exigeComentario(s), manejador == null ? "Rechazar" : manejador.accionRechazo(),
				manejador == null ? List.of() : manejador.llamadas(s), puede && propia, puede && propia && s.getTipo().sensible(),
				s.isDirecta(), enEspera, vence, s.getFrenadoPor(), s.getMotivoFreno(), puedeFrenar);
	}

	/** Si una directa con espera todavía no se aplicó (la cuenta no tiene esta solicitud enlazada). */
	private boolean esperaPendiente(SolicitudCambio s) {
		if (!"usuario".equals(s.getEntidad()) || (s.getTipo() != TipoSolicitud.CAMBIO_ROLES
				&& s.getTipo() != TipoSolicitud.ESTADO_CUENTA)) {
			return false;
		}
		return usuarios.findById(s.getEntidadId()).map(u -> {
			Long enlazada = s.getTipo() == TipoSolicitud.CAMBIO_ROLES ? u.getRolesSolicitudId() : u.getEstadoSolicitudId();
			return (enlazada == null || enlazada < s.getId()) && espera.requiere(s);
		}).orElse(false);
	}

	private LocalDateTime ahora() {
		return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		return autenticacion == null ? "sistema" : autenticacion.getName();
	}

	/** Para la vista: la fecha de un vencimiento, en palabras. */
	static String formatear(LocalDateTime fecha) {
		return fecha == null ? null : Calendario.formatear(fecha.toLocalDate()) + " " + fecha.format(
				DateTimeFormatter.ofPattern("HH:mm"));
	}
}
