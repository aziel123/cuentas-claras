package pe.edu.virgenmaria.cuentasclaras.conectores.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.dto.ConfiguracionConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.ConectorColegio;
import pe.edu.virgenmaria.cuentasclaras.conectores.model.EstadoConector;
import pe.edu.virgenmaria.cuentasclaras.conectores.repository.ConectorColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.SegundaFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sprint 11, tanda 1c (decisión 218, A1c-2 y A1c-5): la espera de 24 horas de la cuenta bancaria, la pasarela o la
 * recaudación que aprobó directo una Promotoría.
 * <ul>
 *   <li><b>Aprobar ahora</b>: el socio (OTRA Promotoría con 30 días en el rol) con su firma y su código atado a ESE
 *       conector; entra en vigencia al instante.</li>
 *   <li><b>Frenar</b>: otra Promotoría con 30 días en el rol, con su firma y un motivo (FRENADO: no entra; se vuelve a
 *       proponer). El operador lo frena desde la plataforma ({@link #frenarComoOperador}).</li>
 *   <li><b>Al vencer</b>: {@code sistema.conectores} lo pone VIGENTE y el anterior pasa a REEMPLAZADO.</li>
 * </ul>
 * Cada paso deja su evento resaltado y avisa a todas las Promotorías y al correo de control. En MySQL lo exige
 * trg_conector_colegio_estado (con el reloj real: SET timestamp no acorta la espera).
 */
@Service
public class EsperaConectores {

	/** La espera (la base la lee de configuracion_bd: 24 horas en prod). */
	public static final Duration ESPERA = Duration.ofHours(24);

	private final ConectorColegioRepository conectores;

	private final ManejadorCambioConector manejador;

	private final ConectoresColegio vigentes;

	private final SegundaFirma segundaFirma;

	private final CodigoPromotoria codigos;

	private final UsuarioRepository usuarios;

	private final FirmaSesion firma;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public EsperaConectores(ConectorColegioRepository conectores, ManejadorCambioConector manejador,
			ConectoresColegio vigentes, SegundaFirma segundaFirma, CodigoPromotoria codigos, UsuarioRepository usuarios,
			FirmaSesion firma, AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock reloj) {
		this.conectores = conectores;
		this.manejador = manejador;
		this.vigentes = vigentes;
		this.segundaFirma = segundaFirma;
		this.codigos = codigos;
		this.usuarios = usuarios;
		this.firma = firma;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	/** Lo que autoriza el código del socio (la huella de ESTE conector) y lo que dice su WhatsApp. */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional(readOnly = true)
	public CodigoPromotoria.Accion accionDelSocio(Long conectorId) {
		ConectorColegio c = enEspera(conectores.findById(conectorId));
		return new CodigoPromotoria.Accion(c.huellaAccion(), "Aprobar ahora " + c.getTipo().descripcion() + " "
				+ c.getProveedor().etiqueta() + " (" + ConfiguracionConector.deJson(c.getTipo(), c.getConfiguracion())
						.resumen() + ")");
	}

	/** «Enviar código» del socio para aprobar al instante (sin transacción abierta: el código va por cc_sistema). */
	@PreAuthorize("hasRole('PROMOTOR')")
	public CodigoPromotoria.Pedido pedirCodigoDelSocio(Long conectorId) {
		return codigos.pedir(accionDelSocio(conectorId));
	}

	/** Solo en dev y en la demo: el código del pedido, para mostrarlo en la pantalla. */
	public Optional<String> codigoDeDemostracion(CodigoPromotoria.Pedido pedido) {
		return pedido == null ? Optional.empty() : codigos.codigoDeDemostracion(pedido.mensajeId());
	}

	/** El socio lo aprueba al instante, con su firma y su código atado a este conector. */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional
	public void aprobarAhora(Long conectorId, CodigoPromotoria.Escrito escrito) {
		String socio = usuario();
		ConectorColegio conector = enEspera(conectores.bloquear(conectorId));
		if (Objects.equals(socio, conector.getPropuestoPor())) {
			throw new ReglaNegocioException("Tú lo propusiste: lo aprueba al instante otra Promotoría (tu socio). Si no, entra "
					+ "en vigencia solo al terminar la espera.");
		}
		if (!segundaFirma.promotoriaAntigua(socio)) {
			throw new ReglaNegocioException("Aprueba al instante una Promotoría con 30 días o más en el rol. Una cuenta de "
					+ "Promotoría nueva no puede saltarse la espera.");
		}
		CodigoPromotoria.Valido valido = codigos.exigir(escrito, conector.huellaAccion());
		firma.firmar(ClaveFirma.conectorAprobadoAhora(conector.getId()));
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		ConectorColegio anterior = reemplazarVigente(conector);
		conector.aprobarAhora(socio, valido.mensajeId(), valido.sha256(), ahora);
		conectores.saveAndFlush(conector);
		vigentes.olvidar(conector.getId());
		manejador.avisarVigencia(conector, anterior, conector.getSolicitudId(), socio + " (al instante, con su código)",
				ahora);
	}

	/** Otra Promotoría con 30 días en el rol lo frena durante la espera, con su firma y un motivo. */
	@PreAuthorize("hasRole('PROMOTOR')")
	@Transactional
	public void frenar(Long conectorId, String motivo) {
		String quien = usuario();
		ConectorColegio conector = enEspera(conectores.bloquear(conectorId));
		if (Objects.equals(quien, conector.getPropuestoPor())) {
			throw new ReglaNegocioException("Tú lo propusiste: lo frena otra Promotoría o un operador (si no fuiste tú, cambia "
					+ "tu clave y avisa hoy al proveedor).");
		}
		if (!segundaFirma.promotoriaAntigua(quien)) {
			throw new ReglaNegocioException("Frena un conector en espera otra Promotoría con 30 días o más en el rol, o un "
					+ "operador de la plataforma.");
		}
		firma.firmar(ClaveFirma.conectorFrenado(conector.getId()));
		frenarEnCurso(conector, quien, motivo, usuarios.findByNombreUsuario(quien).map(Usuario::getNombreCompleto)
				.orElse(quien));
	}

	/**
	 * El operador de la plataforma lo frena (con su firma ya dada en el colegio de plataforma), en la transacción de
	 * {@code cc_sistema} que abrió la plataforma con el colegio en contexto.
	 */
	@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
	@Transactional(propagation = Propagation.MANDATORY)
	public String frenarComoOperador(Long conectorId, String motivo) {
		String operador = usuario();
		ConectorColegio conector = enEspera(conectores.bloquear(conectorId));
		frenarEnCurso(conector, operador, motivo, "El operador " + operador + " de la plataforma");
		return conector.getTipo().descripcion() + " " + conector.getProveedor().etiqueta();
	}

	private void frenarEnCurso(ConectorColegio conector, String quien, String motivo, String nombre) {
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		conector.frenar(quien, motivo, ahora, conector.venceEspera(ESPERA));
		conectores.saveAndFlush(conector);
		vigentes.olvidar(conector.getId());
		String que = "cambiar " + conector.getTipo().descripcion() + " a " + conector.getProveedor().etiqueta();
		auditoria.registrar(AccionAuditoria.CONECTOR_FRENADO, PropuestaConector.ENTIDAD, conector.getId().toString(),
				EstadoConector.EN_ESPERA.name(), EstadoConector.FRENADO.name(), que + " (lo propuso "
						+ conector.getPropuestoPor() + "): lo frenó " + quien + ". Motivo: " + conector.getMotivoFreno()
						+ ". No entra en vigencia; sigue el anterior.");
		eventos.publishEvent(new AvisoPlataforma(ContextoColegio.actual(), AvisoPlataforma.Tipo.DIRECTA_FRENADA,
				List.of(nombre, que, conector.getMotivoFreno()), PropuestaConector.ENTIDAD, conector.getId(), null, null,
				false, true, true));
	}

	/** Los conectores en espera del colegio en contexto, para la pantalla del operador (que los puede frenar). */
	@PreAuthorize("hasRole('OPERADOR_PLATAFORMA')")
	@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
	public List<pe.edu.virgenmaria.cuentasclaras.colegio.service.EsperaVista> enEsperaParaOperador() {
		return conectores.findAllByOrderByIdDesc().stream().filter(c -> c.getEstado() == EstadoConector.EN_ESPERA)
				.map(c -> new pe.edu.virgenmaria.cuentasclaras.colegio.service.EsperaVista("CONECTOR", c.getId(),
						"Cambiar " + c.getTipo().descripcion() + " a " + c.getProveedor().etiqueta() + " ("
								+ ConfiguracionConector.deJson(c.getTipo(), c.getConfiguracion()).resumen() + ")",
						c.getPropuestoPor(), c.venceEspera(ESPERA)))
				.toList();
	}

	/** Los conectores en espera vencida del colegio en contexto (para {@code sistema.conectores}). */
	@PreAuthorize("hasRole('SISTEMA_CONECTORES')")
	@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
	public List<Long> vencidos() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		return conectores.findAllByOrderByIdDesc().stream().filter(c -> c.getEstado() == EstadoConector.EN_ESPERA)
				.filter(c -> {
					LocalDateTime vence = c.venceEspera(ESPERA);
					return vence != null && !vence.isAfter(ahora);
				}).map(ConectorColegio::getId).toList();
	}

	/** {@code sistema.conectores}: al vencer la espera sin freno, el conector entra en vigencia (y el anterior se reemplaza). */
	@PreAuthorize("hasRole('SISTEMA_CONECTORES')")
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean aplicarAlVencer(Long conectorId) {
		ConectorColegio conector = conectores.bloquear(conectorId).orElse(null);
		if (conector == null || conector.getEstado() != EstadoConector.EN_ESPERA) {
			return false;
		}
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		LocalDateTime vence = conector.venceEspera(ESPERA);
		if (vence == null || vence.isAfter(ahora)) {
			return false;
		}
		ConectorColegio anterior = reemplazarVigente(conector);
		conector.aplicarAlVencer();
		conectores.saveAndFlush(conector);
		vigentes.olvidar(conector.getId());
		manejador.avisarVigencia(conector, anterior, conector.getSolicitudId(), conector.getResueltoPor()
				+ " (al terminar la espera de 24 horas)", ahora);
		return true;
	}

	/** Hasta cuándo espera (la fecha de la base, o la aprobación más 24 horas en H2). */
	public static LocalDateTime venceEspera(ConectorColegio conector) {
		return conector.venceEspera(ESPERA);
	}

	private ConectorColegio reemplazarVigente(ConectorColegio conector) {
		Optional<ConectorColegio> anterior = conectores.bloquearVigente(conector.getTipo());
		anterior.ifPresent(a -> {
			a.reemplazar();
			// Primero el anterior: el UNIQUE (colegio, tipo, vigente) admite un solo vigente.
			conectores.saveAndFlush(a);
			vigentes.olvidar(a.getId());
		});
		return anterior.orElse(null);
	}

	private static ConectorColegio enEspera(Optional<ConectorColegio> leido) {
		ConectorColegio conector = leido.orElseThrow(() -> new RecursoNoEncontradoException("Conector no encontrado"));
		if (conector.getEstado() != EstadoConector.EN_ESPERA) {
			throw new ReglaNegocioException("Este conector no está en espera (" + conector.getEstado().etiqueta()
					.toLowerCase() + ").");
		}
		return conector;
	}

	private static String usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null) {
			throw new AccessDeniedException("Se requiere un usuario en sesión");
		}
		return autenticacion.getName();
	}
}
