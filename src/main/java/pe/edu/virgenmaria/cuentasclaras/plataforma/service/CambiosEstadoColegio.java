package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AmbitoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.CambioEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.MotivoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.CambioEstadoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.CambioEstadoVista;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PendientesCicloDeVida;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.EnlaceActivacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.SesionUsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CierreSesionesColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Sprint 11, tanda 1 (secciones 3.3 a 3.6 y 3.13): el estado del colegio y la facultad del titular los deciden DOS
 * operadores. Uno PROPONE (con la firma de su sesión, {@code cambio_estado_colegio:{colegio}:{n}}) y OTRO APRUEBA
 * ({@code :APROBADO}); también se RECHAZA (otro operador) o se ANULA (cualquiera, antes de aplicar), y un operador que no
 * propuso RESUELVE la objeción del titular ({@code :OBJECION_RESUELTA}). La BAJA inmediata, la REACTIVACION y el bloqueo de
 * la facultad se aplican en la misma transacción de la aprobación; la SUSPENSION (a los 30 días) y la baja a los 7 días las
 * aplica {@code sistema.plataforma} en su fecha ({@code AplicacionCambiosEstado}). Cada paso avisa, en la misma
 * transacción, al titular, a toda la Promotoría, al correo institucional y al correo de control; y después a los
 * operadores y a la bitácora de la plataforma. Escribe con {@code cc_sistema} por {@link EjecucionPlataforma}; en MySQL lo
 * vigilan trg_cambio_estado_colegio_* y trg_colegio_cambio.
 * <p>
 * AP-8: la SUSPENSION no se puede proponer hasta que exista la exportación de los datos (V40, tanda 2): la base la rechaza
 * y la aplicación también ({@link #suspensionDisponible()}).
 */
@Service
@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
public class CambiosEstadoColegio {

	/**
	 * AP-8: hasta V40 (la exportación completa de los datos del colegio), no hay suspensión. Las pruebas de H2 la encienden
	 * ({@code cuentasclaras.ciclo.suspension-disponible}) para probar el flujo; en MySQL la base la rechaza igual
	 * (trg_cambio_estado_colegio_nace exige la exportación).
	 */
	private final boolean suspensionDisponible;

	private final ColegioRepository colegios;

	private final CambioEstadoColegioRepository cambios;

	private final PrimeraPromotoriaRepository primeras;

	private final SesionUsuarioRepository sesiones;

	private final EnlaceActivacionRepository enlaces;

	private final PendientesCicloDeVida pendientes;

	private final CierreSesionesColegio cierreSesiones;

	private final AplicadorCambioEstado aplicador;

	private final EjecucionPlataforma plataforma;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transaccion;

	private final TransactionTemplate lectura;

	private final Clock reloj;

	public CambiosEstadoColegio(ColegioRepository colegios, CambioEstadoColegioRepository cambios,
			PrimeraPromotoriaRepository primeras, SesionUsuarioRepository sesiones, EnlaceActivacionRepository enlaces,
			PendientesCicloDeVida pendientes, CierreSesionesColegio cierreSesiones, EjecucionPlataforma plataforma,
			FirmaSesion firma, AuditoriaService auditoria, ApplicationEventPublisher eventos,
			PlatformTransactionManager transacciones, Clock reloj, AplicadorCambioEstado aplicador,
			@org.springframework.beans.factory.annotation.Value("${cuentasclaras.ciclo.suspension-disponible:false}")
			boolean suspensionDisponible) {
		this.suspensionDisponible = suspensionDisponible;
		this.aplicador = aplicador;
		this.colegios = colegios;
		this.cambios = cambios;
		this.primeras = primeras;
		this.sesiones = sesiones;
		this.enlaces = enlaces;
		this.pendientes = pendientes;
		this.cierreSesiones = cierreSesiones;
		this.plataforma = plataforma;
		this.firma = firma;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.reloj = reloj;
	}

	/** AP-8: la suspensión se habilita con la exportación de los datos (tanda 2). */
	public boolean suspensionDisponible() {
		return suspensionDisponible;
	}

	/** El operador A propone. Devuelve el id del cambio. */
	public Long proponer(long colegioId, TipoCambioEstado tipo, MotivoCambioEstado motivoTipo, String motivo,
			String referenciaAviso) {
		UsuarioAutenticado operador = operador();
		Objects.requireNonNull(tipo, "tipo");
		if (tipo == TipoCambioEstado.SUSPENSION && !suspensionDisponible()) {
			throw new ReglaNegocioException("La suspensión estará disponible cuando esté la exportación completa de los datos "
					+ "del colegio (tanda 2): el colegio debe recibir sus datos el día que se aplica.");
		}
		Colegio colegio = colegio(colegioId);
		// Antes de firmar (la firma es única por clave): todo lo que rechazaría la propuesta.
		long numero = ContextoColegio.en(colegioId, () -> lectura.execute(t -> {
			validarPropuesta(colegio, tipo, motivoTipo, motivo, referenciaAviso, operador);
			return cambios.count() + 1;
		}));
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioEstadoPropuesto(colegioId, numero)));
		Long id = ContextoColegio.en(colegioId, () -> plataforma.como(() -> {
			validarPropuesta(colegio(colegioId), tipo, motivoTipo, motivo, referenciaAviso, operador);
			CambioEstadoColegio cambio;
			try {
				cambio = cambios.saveAndFlush(CambioEstadoColegio.propuesto(tipo, motivoTipo, motivo, referenciaAviso,
						colegio(colegioId).getEstado(), operador.colegioId(), operador.usuarioId(), operador.getUsername()));
			}
			catch (DataIntegrityViolationException e) {
				throw new ReglaNegocioException("Este colegio ya tiene un cambio pendiente de ese tipo: resuélvelo primero.");
			}
			avisarAlColegio(colegioId, AvisoPlataforma.Tipo.CAMBIO_ESTADO_PROPUESTO, List.of(operador.getUsername(),
					queHace(tipo), cambio.getMotivoTipo().etiqueta() + ": " + cambio.getMotivo()), cambio, "P");
			auditoria.registrar(AccionAuditoria.CAMBIO_ESTADO_COLEGIO_PROPUESTO, "cambio_estado_colegio",
					cambio.getId().toString(), colegio(colegioId).getEstado().name(), tipo.name(), "El operador "
							+ operador.getUsername() + " propuso " + queHace(tipo) + " (" + cambio.getMotivoTipo().etiqueta()
							+ ": " + cambio.getMotivo() + "). No cambia nada hasta que lo apruebe otro operador. Se avisó al "
							+ "titular, a la Promotoría, al correo institucional y al correo de control.");
			return cambio.getId();
		}));
		enLaPlataforma(AvisoPlataforma.Tipo.CAMBIO_ESTADO_PROPUESTO, List.of(operador.getUsername(), queHace(tipo) + " ("
				+ colegio.getNombre() + ")", motivoTipo.etiqueta()), id, "P", AccionAuditoria.CAMBIO_ESTADO_COLEGIO_PROPUESTO,
				"Propuesta de " + queHace(tipo) + " del colegio " + colegio.getNombre() + ".");
		return id;
	}

	/** OTRO operador aprueba (con su firma). Lo inmediato se aplica en la misma transacción. */
	public void aprobar(long colegioId, long cambioId) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		CambioEstadoColegio leido = leer(colegioId, cambioId);
		if (leido.getEstado() != EstadoCambioEstado.PROPUESTO) {
			throw new ReglaNegocioException("Este cambio ya no está por aprobar.");
		}
		if (Objects.equals(leido.getPropuestoPor(), operador.getUsername())) {
			throw new ReglaNegocioException("Tú propusiste este cambio: lo aprueba otro operador, con su propia sesión.");
		}
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioEstado(cambioId, "APROBADO")));
		List<Long> cerradas = new ArrayList<>();
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			LocalDateTime ahora = LocalDateTime.now(reloj);
			CambioEstadoColegio cambio = cambios.bloquear(cambioId)
					.orElseThrow(() -> new RecursoNoEncontradoException("Cambio no encontrado"));
			if (colegio(colegioId).getEstado() != cambio.getEstadoOrigen()) {
				throw new ReglaNegocioException("El colegio ya no está en el estado del cambio: anúlalo y propón otro.");
			}
			cambio.aprobar(operador.getUsername(), operador.usuarioId(), ahora, cuentasActivadas());
			cambios.saveAndFlush(cambio);
			avisarAlColegio(colegioId, AvisoPlataforma.Tipo.CAMBIO_ESTADO_APROBADO, List.of(queHace(cambio.getTipo()),
					Calendario.formatear(cambio.getEfectivaDesde().toLocalDate()), cambio.getPropuestoPor() + " y "
							+ operador.getUsername()), cambio, "A");
			auditoria.registrar(AccionAuditoria.CAMBIO_ESTADO_COLEGIO_APROBADO, "cambio_estado_colegio",
					cambio.getId().toString(), "PROPUESTO", "APROBADO; rige desde " + cambio.getEfectivaDesde(), "El operador "
							+ operador.getUsername() + " aprobó " + queHace(cambio.getTipo()) + " que propuso "
							+ cambio.getPropuestoPor() + ". Rige desde el " + Calendario.formatear(
									cambio.getEfectivaDesde().toLocalDate()) + ".");
			if (!cambio.getEfectivaDesde().isAfter(ahora)) {
				cerradas.addAll(aplicador.aplicar(cambio, ahora));
			}
		}));
		cierreSesiones.expirarHttp(cerradas);
		enLaPlataforma(AvisoPlataforma.Tipo.CAMBIO_ESTADO_APROBADO, List.of(queHace(leido.getTipo()) + " ("
				+ colegio.getNombre() + ")", "su fecha", leido.getPropuestoPor() + " y " + operador.getUsername()), cambioId,
				"A", AccionAuditoria.CAMBIO_ESTADO_COLEGIO_APROBADO, "Aprobación de " + queHace(leido.getTipo())
						+ " del colegio " + colegio.getNombre() + ".");
	}

	/** OTRO operador rechaza una propuesta (con su firma). */
	public void rechazar(long colegioId, long cambioId) {
		resolver(colegioId, cambioId, true);
	}

	/** Cualquier operador anula un cambio pendiente (con su firma), por ejemplo porque el colegio pagó. */
	public void anular(long colegioId, long cambioId) {
		resolver(colegioId, cambioId, false);
	}

	private void resolver(long colegioId, long cambioId, boolean rechazo) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		CambioEstadoColegio leido = leer(colegioId, cambioId);
		if (rechazo && leido.getEstado() != EstadoCambioEstado.PROPUESTO) {
			throw new ReglaNegocioException("Solo se rechaza un cambio propuesto; uno aprobado se anula.");
		}
		if (!leido.getEstado().pendiente()) {
			throw new ReglaNegocioException("Este cambio ya está resuelto.");
		}
		if (rechazo && Objects.equals(leido.getPropuestoPor(), operador.getUsername())) {
			throw new ReglaNegocioException("Tú propusiste este cambio: lo rechaza otro operador. Si ya no hace falta, anúlalo.");
		}
		String resolucion = rechazo ? "RECHAZADO" : "ANULADO";
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioEstado(cambioId, resolucion)));
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			LocalDateTime ahora = LocalDateTime.now(reloj);
			CambioEstadoColegio cambio = cambios.bloquear(cambioId)
					.orElseThrow(() -> new RecursoNoEncontradoException("Cambio no encontrado"));
			if (rechazo) {
				cambio.rechazar(operador.getUsername(), ahora);
			}
			else {
				cambio.anular(operador.getUsername(), ahora);
			}
			cambios.saveAndFlush(cambio);
			avisarAlColegio(colegioId, AvisoPlataforma.Tipo.CAMBIO_ESTADO_RESUELTO, List.of(queHace(cambio.getTipo()),
					rechazo ? "rechazó" : "anuló", operador.getUsername()), cambio, rechazo ? "R" : "N");
			auditoria.registrar(rechazo ? AccionAuditoria.CAMBIO_ESTADO_COLEGIO_RECHAZADO
					: AccionAuditoria.CAMBIO_ESTADO_COLEGIO_ANULADO, "cambio_estado_colegio", cambio.getId().toString(),
					leido.getEstado().name(), resolucion, "El operador " + operador.getUsername() + (rechazo ? " rechazó "
							: " anuló ") + queHace(cambio.getTipo()) + ". No cambia nada.");
		}));
		enLaPlataforma(AvisoPlataforma.Tipo.CAMBIO_ESTADO_RESUELTO, List.of(queHace(leido.getTipo()) + " ("
				+ colegio.getNombre() + ")", rechazo ? "rechazó" : "anuló", operador.getUsername()), cambioId,
				rechazo ? "R" : "N", rechazo ? AccionAuditoria.CAMBIO_ESTADO_COLEGIO_RECHAZADO
						: AccionAuditoria.CAMBIO_ESTADO_COLEGIO_ANULADO, (rechazo ? "Rechazo" : "Anulación") + " de "
								+ queHace(leido.getTipo()) + " del colegio " + colegio.getNombre() + ".");
	}

	/** AP-10: un operador que no propuso responde la objeción del titular (con su firma). */
	public void resolverObjecion(long colegioId, long cambioId, String respuesta) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		CambioEstadoColegio leido = leer(colegioId, cambioId);
		if (leido.getObjecion() == null || !leido.objecionAbierta()) {
			throw new ReglaNegocioException("Este cambio no tiene una objeción por responder.");
		}
		if (Objects.equals(leido.getPropuestoPor(), operador.getUsername())) {
			throw new ReglaNegocioException("Tú propusiste este cambio: la objeción la responde otro operador.");
		}
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioEstado(cambioId, "OBJECION_RESUELTA")));
		ContextoColegio.en(colegioId, () -> plataforma.ejecutar(() -> {
			CambioEstadoColegio cambio = cambios.bloquear(cambioId)
					.orElseThrow(() -> new RecursoNoEncontradoException("Cambio no encontrado"));
			cambio.resolverObjecion(operador.getUsername(), respuesta, LocalDateTime.now(reloj));
			cambios.saveAndFlush(cambio);
			avisarAlColegio(colegioId, AvisoPlataforma.Tipo.OBJECION_RESPONDIDA, List.of(operador.getUsername(),
					queHace(cambio.getTipo()), cambio.getObjecionRespuesta()), cambio, "O");
			auditoria.registrar(AccionAuditoria.OBJECION_RESUELTA, "cambio_estado_colegio", cambio.getId().toString(),
					"objeción abierta", "objeción resuelta", "El operador " + operador.getUsername() + " respondió la "
							+ "objeción del titular: " + cambio.getObjecionRespuesta());
		}));
		enLaPlataforma(AvisoPlataforma.Tipo.OBJECION_RESPONDIDA, List.of(operador.getUsername(), queHace(leido.getTipo())
				+ " (" + colegio.getNombre() + ")", "respondida"), cambioId, "O", AccionAuditoria.OBJECION_RESUELTA,
				"Respuesta a la objeción del titular del colegio " + colegio.getNombre() + ".");
	}

	/** La bandeja: los cambios pendientes de todos los colegios, con lo que esperan. */
	public List<CambioEstadoVista> bandeja() {
		UsuarioAutenticado operador = operador();
		List<CambioEstadoVista> vistas = new ArrayList<>();
		for (Colegio c : lectura.execute(t -> colegios.findByTipoOrderByIdAsc(TipoColegio.COLEGIO))) {
			vistas.addAll(ContextoColegio.en(c.getId(), () -> lectura.execute(t -> cambios.findByEstadoInOrderByIdAsc(
					EnumSet.of(EstadoCambioEstado.PROPUESTO, EstadoCambioEstado.APROBADO)).stream()
					.map(x -> vista(c, x, operador)).toList())));
		}
		return vistas;
	}

	/** La historia de un colegio (todos sus cambios), para su ficha. */
	public List<CambioEstadoVista> historial(long colegioId) {
		UsuarioAutenticado operador = operador();
		Colegio colegio = colegio(colegioId);
		return ContextoColegio.en(colegioId, () -> lectura.execute(t -> cambios.findAllByOrderByIdDesc().stream()
				.map(x -> vista(colegio, x, operador)).toList()));
	}

	private void validarPropuesta(Colegio colegio, TipoCambioEstado tipo, MotivoCambioEstado motivoTipo, String motivo,
			String referenciaAviso, UsuarioAutenticado operador) {
		// Las reglas del cambio (motivo, referencia, origen) las comprueba la entidad.
		CambioEstadoColegio.propuesto(tipo, motivoTipo, motivo, referenciaAviso, colegio.getEstado(), operador.colegioId(),
				operador.usuarioId(), operador.getUsername());
		if (cambios.findFirstByAmbitoAndEstadoIn(tipo.ambito(), EnumSet.of(EstadoCambioEstado.PROPUESTO,
				EstadoCambioEstado.APROBADO)).isPresent()) {
			throw new ReglaNegocioException("Este colegio ya tiene un cambio pendiente: resuélvelo (o anúlalo) primero.");
		}
		if (tipo == TipoCambioEstado.BAJA) {
			List<String> impiden = pendientes.impidenLaBaja();
			if (!impiden.isEmpty()) {
				throw new ReglaNegocioException("No se da de baja: " + String.join("; ", impiden) + ".");
			}
		}
		if (tipo == TipoCambioEstado.BLOQUEO_FACULTAD && colegio.isFacultadBloqueada()) {
			throw new ReglaNegocioException("La facultad del titular ya está bloqueada.");
		}
		if (tipo == TipoCambioEstado.DESBLOQUEO_FACULTAD && !colegio.isFacultadBloqueada()) {
			throw new ReglaNegocioException("La facultad del titular no está bloqueada.");
		}
	}

	/** AP-6: si alguna cuenta del colegio se activó (usó su enlace o abrió una sesión): la baja va a los 7 días. */
	private boolean cuentasActivadas() {
		return sesiones.existsByIdNotNull() || enlaces.existsByUsadoEnIsNotNull();
	}

	private void avisarAlColegio(long colegioId, AvisoPlataforma.Tipo tipo, List<String> parametros,
			CambioEstadoColegio cambio, String sufijo) {
		aplicador.avisarAlColegio(colegioId, tipo, parametros, cambio, sufijo);
	}

	/** En el colegio de plataforma (el contexto del operador): el aviso a los operadores y el evento en su bitácora. */
	private void enLaPlataforma(AvisoPlataforma.Tipo tipo, List<String> parametros, Long cambioId, String sufijo,
			AccionAuditoria accion, String detalle) {
		UsuarioAutenticado operador = operador();
		plataforma.ejecutar(() -> {
			eventos.publishEvent(new AvisoOperadores(operador.colegioId(), tipo, parametros, "cambio_estado_colegio",
					cambioId, sufijo));
			auditoria.registrar(accion, "cambio_estado_colegio", cambioId.toString(), null, null, detalle);
		});
	}

	private CambioEstadoVista vista(Colegio c, CambioEstadoColegio x, UsuarioAutenticado operador) {
		String bloqueada = null;
		if (x.getTipo() == TipoCambioEstado.SUSPENSION && x.getEstado() == EstadoCambioEstado.APROBADO) {
			List<String> esperan = new ArrayList<>(pendientes.esperanLaSuspension());
			if (x.objecionAbierta()) {
				esperan.add("la objeción del titular sin responder");
			}
			bloqueada = esperan.isEmpty() ? null : String.join(", ", esperan);
		}
		boolean ajeno = !Objects.equals(x.getPropuestoPor(), operador.getUsername());
		return new CambioEstadoVista(x.getId(), c.getId(), c.getNombre(), x.getTipo().name(), x.getTipo().etiqueta(),
				x.getEstado().name(), x.getEstado().etiqueta(), x.getMotivoTipo().etiqueta(), x.getMotivo(),
				x.getReferenciaAviso(), x.getPropuestoPor(), x.getCreadoEn(), x.getAprobadoPor(), x.getEfectivaDesde(),
				x.getObjecion(), x.getObjetadoPor(), x.getObjecionRespuesta(), x.getObjecionResueltaPor(),
				ajeno && x.getEstado() == EstadoCambioEstado.PROPUESTO, ajeno && x.objecionAbierta(), bloqueada);
	}

	private CambioEstadoColegio leer(long colegioId, long cambioId) {
		return ContextoColegio.en(colegioId, () -> lectura.execute(t -> cambios.findById(cambioId)))
				.orElseThrow(() -> new RecursoNoEncontradoException("Cambio no encontrado"));
	}

	private Colegio colegio(long colegioId) {
		return lectura.execute(t -> colegios.findById(colegioId).filter(c -> !c.esPlataforma()))
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
	}

	/** Lo que hace el cambio, en palabras (va como parámetro del aviso: la plantilla no lo dice). */
	static String queHace(TipoCambioEstado tipo) {
		return switch (tipo) {
			case BAJA -> "dar de baja el colegio";
			case SUSPENSION -> "suspender el acceso del personal";
			case REACTIVACION -> "reactivar el acceso del personal";
			case CIERRE -> "cerrar el colegio (las familias consultan hasta la fecha de supresión)";
			case BLOQUEO_FACULTAD -> "bloquear la facultad del titular";
			case DESBLOQUEO_FACULTAD -> "desbloquear la facultad del titular";
		};
	}

	private static UsuarioAutenticado operador() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)) {
			throw new AccessDeniedException("Se requiere un operador en sesión");
		}
		return usuario;
	}
}
