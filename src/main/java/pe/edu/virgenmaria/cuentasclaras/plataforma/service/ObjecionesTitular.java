package pe.edu.virgenmaria.cuentasclaras.plataforma.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.CambioEstadoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.CambioEstadoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoOperadores;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.plataforma.dto.CambioDelColegioVista;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Sprint 11, tanda 1 (AP-10): el titular del colegio ve los cambios de estado de su colegio y OBJETA una baja o una
 * suspensión pendiente, con la firma de su sesión ({@code cambio_estado_colegio:{id}:OBJETADO}). Mientras la objeción no
 * la responda un operador que no propuso el cambio, el cambio no se aplica. Escribe con {@code cc_sistema} por
 * {@link EjecucionPlataforma}; en MySQL lo exige trg_cambio_estado_colegio_estado (titular con la facultad sin bloquear y
 * con su firma). Avisa en la misma transacción al correo de control y a la Promotoría, y después a los operadores.
 */
@Service
public class ObjecionesTitular {

	private static final Logger LOG = LoggerFactory.getLogger(ObjecionesTitular.class);

	private final ColegioRepository colegios;

	private final CambioEstadoColegioRepository cambios;

	private final PrimeraPromotoriaRepository primeras;

	private final AplicadorCambioEstado aplicador;

	private final EjecucionPlataforma plataforma;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final TransactionTemplate transaccion;

	private final TransactionTemplate lectura;

	private final Clock reloj;

	public ObjecionesTitular(ColegioRepository colegios, CambioEstadoColegioRepository cambios,
			PrimeraPromotoriaRepository primeras, AplicadorCambioEstado aplicador, EjecucionPlataforma plataforma,
			FirmaSesion firma, AuditoriaService auditoria, ApplicationEventPublisher eventos,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.colegios = colegios;
		this.cambios = cambios;
		this.primeras = primeras;
		this.aplicador = aplicador;
		this.plataforma = plataforma;
		this.firma = firma;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.lectura = new TransactionTemplate(transacciones);
		this.lectura.setReadOnly(true);
		this.reloj = reloj;
	}

	/** Los cambios del colegio en sesión (pendientes primero), para la Promotoría y Dirección. */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public List<CambioDelColegioVista> delColegio() {
		UsuarioAutenticado actor = actor();
		boolean titular = esTitular(actor);
		return lectura.execute(t -> cambios.findAllByOrderByIdDesc().stream().map(c -> new CambioDelColegioVista(c.getId(),
				c.getTipo().etiqueta(), c.getEstado().name(), c.getEstado().etiqueta(), c.getMotivoTipo().etiqueta(),
				c.getMotivo(), c.getReferenciaAviso(), c.getCreadoEn(), c.getEfectivaDesde(), c.getObjecion(),
				c.getObjecionRespuesta(), c.getObjecionResueltaPor(), titular && c.getTipo().objetable()
						&& c.getEstado().pendiente() && c.getObjecion() == null)).toList());
	}

	/** {@code true} si quien está en sesión es el titular (la primera Promotoría) con su facultad sin bloquear. */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR')")
	public boolean puedeObjetar() {
		return esTitular(actor());
	}

	/** El titular objeta una baja o una suspensión pendiente de su colegio (una vez, con su firma). */
	@PreAuthorize("hasRole('PROMOTOR')")
	public void objetar(long cambioId, String texto) {
		UsuarioAutenticado actor = actor();
		Long colegioId = ContextoColegio.actual();
		if (!esTitular(actor)) {
			throw new AccessDeniedException("Solo el titular del colegio objeta un cambio de estado");
		}
		CambioEstadoColegio leido = lectura.execute(t -> cambios.findById(cambioId))
				.orElseThrow(() -> new RecursoNoEncontradoException("Cambio no encontrado"));
		// Antes de firmar: lo que rechazaría la objeción (la firma es única por clave).
		CambioEstadoColegio prueba = lectura.execute(t -> cambios.findById(cambioId)).orElseThrow();
		prueba.objetar(actor.getUsername(), texto, LocalDateTime.now(reloj));
		transaccion.executeWithoutResult(t -> firma.firmar(ClaveFirma.cambioEstado(cambioId, "OBJETADO")));
		plataforma.ejecutar(() -> {
			CambioEstadoColegio cambio = cambios.bloquear(cambioId)
					.orElseThrow(() -> new RecursoNoEncontradoException("Cambio no encontrado"));
			if (!cambio.getEstado().pendiente()) {
				throw new ReglaNegocioException("Este cambio ya está resuelto: ya no se objeta.");
			}
			cambio.objetar(actor.getUsername(), texto, LocalDateTime.now(reloj));
			cambios.saveAndFlush(cambio);
			aplicador.avisarAlColegio(colegioId, AvisoPlataforma.Tipo.OBJECION_TITULAR, List.of(actor.nombreCompleto(),
					CambiosEstadoColegio.queHace(cambio.getTipo()), cambio.getObjecion()), cambio, "J");
			auditoria.registrar(AccionAuditoria.CAMBIO_ESTADO_OBJETADO, "cambio_estado_colegio", cambio.getId().toString(),
					"sin objeción", "objetado", "El titular " + actor.getUsername() + " objetó "
							+ CambiosEstadoColegio.queHace(cambio.getTipo()) + ": " + cambio.getObjecion()
							+ ". No se aplica hasta que responda un operador que no lo propuso.");
		});
		avisarOperadores(actor, colegioId, leido);
	}

	/** Después, en el colegio de plataforma (otra transacción): el aviso a los operadores y su evento. */
	private void avisarOperadores(UsuarioAutenticado actor, Long colegioId, CambioEstadoColegio cambio) {
		try {
			Long idPlataforma = lectura.execute(t -> colegios.findFirstByTipo(TipoColegio.PLATAFORMA).map(Colegio::getId)
					.orElse(null));
			String colegio = lectura.execute(t -> colegios.findById(colegioId).map(Colegio::getNombre).orElse("?"));
			if (idPlataforma == null) {
				return;
			}
			ContextoColegio.en(idPlataforma, () -> plataforma.ejecutar(() -> {
				eventos.publishEvent(new AvisoOperadores(idPlataforma, AvisoPlataforma.Tipo.OBJECION_TITULAR,
						List.of(actor.nombreCompleto() + " (" + colegio + ")", CambiosEstadoColegio.queHace(cambio.getTipo()),
								"revisa la objeción en la bandeja de cambios de estado"), "cambio_estado_colegio",
						cambio.getId(), "J:" + colegioId));
				auditoria.registrar(AccionAuditoria.CAMBIO_ESTADO_OBJETADO, "cambio_estado_colegio",
						cambio.getId().toString(), null, "objetado", "El titular del colegio " + colegio + " objetó "
								+ CambiosEstadoColegio.queHace(cambio.getTipo()) + ".");
			}));
		}
		catch (RuntimeException e) {
			LOG.warn("No se pudo avisar a los operadores de la objeción del colegio {}: {}", colegioId,
					e.getClass().getSimpleName());
		}
	}

	private boolean esTitular(UsuarioAutenticado actor) {
		Long colegioId = ContextoColegio.actual();
		return Boolean.TRUE.equals(lectura.execute(t -> {
			boolean bloqueada = colegios.findById(colegioId).map(Colegio::isFacultadBloqueada).orElse(true);
			return !bloqueada && actor.roles().contains(pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol.PROMOTOR)
					&& primeras.findFirstByIdNotNull().map(p -> Objects.equals(p.getUsuarioId(), actor.usuarioId()))
							.orElse(false);
		}));
	}

	private static UsuarioAutenticado actor() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)) {
			throw new AccessDeniedException("Se requiere una persona en sesión");
		}
		return usuario;
	}
}
