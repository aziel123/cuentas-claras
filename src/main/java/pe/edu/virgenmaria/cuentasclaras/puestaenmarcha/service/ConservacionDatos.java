package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.EstadoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoCambioEstado;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.CambioEstadoColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.FirmaOperacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.CodigoPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Sprint 11, tanda 2 (AP-11 y AP2-9) y correcciones de la auditoría del código (A2): la autorización expresa del titular
 * (la cuenta de la primera Promotoría) para que la plataforma conserve los datos del colegio 12 meses desde que se aplica
 * una suspensión. Es el paso 16 de la Puesta en marcha, obligatorio.
 * <ul>
 * <li>La da SOLO el titular, con su firma numerada ({@code colegio:{id}:CONSERVACION:{n}}: puede volver a darla).</li>
 * <li>El RETIRO no es inmediato: lo pide el titular con su firma ({@code :CONSERVACION_RETIRADA:{n}}) y el código que le
 * llega por WhatsApp para ESA acción; se aplica a las 24 horas ({@code AplicacionRetirosConservacion}) y un operador puede
 * frenarlo antes.</li>
 * <li>Con una suspensión aprobada (o desde la suspensión) no cambia.</li>
 * </ul>
 * Cada paso avisa al correo de control. La base lo exige igual (trg_colegio_cambio).
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
@Transactional
public class ConservacionDatos {

	/** Lo que ve la pantalla. */
	public record Vista(boolean esTitular, boolean autorizada, String autorizadaPor, LocalDateTime autorizadaEn,
			boolean editable, boolean puedeAutorizar, boolean puedeRetirar, LocalDateTime retiroAplicaDesde) {
	}

	private final ColegioRepository colegios;

	private final CambioEstadoColegioRepository cambios;

	private final PrimeraPromotoriaRepository primeras;

	private final FirmaSesion firma;

	private final FirmaOperacionRepository firmas;

	private final CodigoPromotoria codigos;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public ConservacionDatos(ColegioRepository colegios, CambioEstadoColegioRepository cambios,
			PrimeraPromotoriaRepository primeras, FirmaSesion firma, FirmaOperacionRepository firmas,
			CodigoPromotoria codigos, AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock reloj) {
		this.colegios = colegios;
		this.cambios = cambios;
		this.primeras = primeras;
		this.firma = firma;
		this.firmas = firmas;
		this.codigos = codigos;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	@PreAuthorize("hasAnyRole('PROMOTOR', 'DIRECTOR', 'ADMINISTRACION')")
	public Vista vista() {
		Colegio c = colegio();
		boolean titular = esTitular();
		boolean editable = c.getEstado().opera() && !suspensionAprobada();
		return new Vista(titular, c.conservacionAutorizada(), c.getConservacionAutorizadaPor(),
				c.getConservacionAutorizadaEn(), editable, titular && editable && !c.conservacionAutorizada(),
				titular && editable && c.conservacionAutorizada() && c.getConservacionRetiroPor() == null,
				c.getConservacionRetiroAplicaDesde());
	}

	public void autorizar() {
		Colegio c = colegio();
		UsuarioAutenticado titular = titular();
		exigirSinSuspensionAprobada();
		long n = firmas.countByClaveStartingWith(ClaveFirma.prefijoColegio(c.getId(), "CONSERVACION")) + 1;
		c.autorizarConservacion(titular.getUsername(), LocalDateTime.now(reloj));
		firma.firmar(ClaveFirma.conservacion(c.getId(), n));
		colegios.saveAndFlush(c);
		auditoria.registrar(AccionAuditoria.CONSERVACION_AUTORIZADA, "colegio", c.getId().toString(), null, "AUTORIZADA",
				titular.nombreCompleto() + ", titular del colegio, autorizó con su firma (vez " + n + ") que la plataforma "
						+ "conserve los datos 12 meses desde que se aplique una suspensión. Se avisó al correo de control.");
		avisar("el titular autorizó conservar los datos", "12 meses desde una suspensión", "AUTORIZADA:" + n);
	}

	/** La acción que firma el código de WhatsApp del retiro (la misma huella que calcula la base). */
	public CodigoPromotoria.Accion accionRetiro() {
		Colegio c = colegio();
		long n = firmas.countByClaveStartingWith(ClaveFirma.prefijoColegio(c.getId(), "CONSERVACION_RETIRADA")) + 1;
		return new CodigoPromotoria.Accion(Sha256.hex("CONSERVACION_RETIRADA|" + c.getId() + "|" + n),
				"Retirar la autorización de conservar los datos del colegio (se aplica a las 24 horas)");
	}

	/** El titular pide el código de WhatsApp para retirar la autorización (la identidad abre su propia transacción). */
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	public CodigoPromotoria.Pedido pedirCodigoRetiro() {
		titular();
		exigirSinSuspensionAprobada();
		return codigos.pedir(accionRetiro());
	}

	/** El titular PIDE el retiro con su firma y el código: se aplica a las 24 horas si un operador no lo frena. */
	public LocalDateTime pedirRetiro(CodigoPromotoria.Escrito escrito) {
		Colegio c = colegio();
		UsuarioAutenticado titular = titular();
		exigirSinSuspensionAprobada();
		CodigoPromotoria.Accion accion = accionRetiro();
		CodigoPromotoria.Valido valido = codigos.exigir(escrito, accion.huella());
		long n = firmas.countByClaveStartingWith(ClaveFirma.prefijoColegio(c.getId(), "CONSERVACION_RETIRADA")) + 1;
		c.pedirRetiroConservacion(titular.getUsername(), valido.mensajeId(), valido.sha256(), LocalDateTime.now(reloj));
		firma.firmar(ClaveFirma.conservacionRetirada(c.getId(), n));
		colegios.saveAndFlush(c);
		LocalDateTime aplica = c.getConservacionRetiroAplicaDesde();
		auditoria.registrar(AccionAuditoria.CONSERVACION_RETIRO_PEDIDO, "colegio", c.getId().toString(), "AUTORIZADA",
				"RETIRO " + aplica, titular.nombreCompleto() + ", titular del colegio, pidió con su firma y su código retirar "
						+ "la autorización de conservación. Se aplica a las 24 horas si un operador no lo frena. Se avisó al "
						+ "correo de control.");
		avisar("el titular pidió retirar la autorización de conservar los datos",
				"se aplica el " + Calendario.formatear(aplica.toLocalDate()) + " si nadie lo frena", "RETIRO:" + n);
		return aplica;
	}

	private void avisar(String que, String detalle, String sufijo) {
		Long colegioId = ContextoColegio.actual();
		Long titularId = primeras.findFirstByIdNotNull().map(p -> p.getUsuarioId()).orElse(null);
		eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CONSERVACION, List.of(que, detalle),
				"colegio", colegioId, sufijo, titularId, true, true, true));
	}

	private boolean suspensionAprobada() {
		return !cambios.findByTipoAndEstadoOrderByIdAsc(TipoCambioEstado.SUSPENSION, EstadoCambioEstado.APROBADO).isEmpty();
	}

	private void exigirSinSuspensionAprobada() {
		if (suspensionAprobada()) {
			throw new ReglaNegocioException("El colegio tiene una suspensión aprobada: la autorización de conservación queda "
					+ "como está.");
		}
	}

	private boolean esTitular() {
		UsuarioAutenticado usuario = usuario();
		return primeras.findFirstByIdNotNull().map(p -> Objects.equals(p.getUsuarioId(), usuario.usuarioId()))
				.orElse(false);
	}

	private UsuarioAutenticado titular() {
		if (!esTitular()) {
			throw new ReglaNegocioException("La autorización de conservación la da o la retira solo el titular del colegio "
					+ "(la primera Promotoría), con su firma.");
		}
		return usuario();
	}

	private Colegio colegio() {
		return colegios.findById(ContextoColegio.actual()).filter(c -> !c.esPlataforma())
				.orElseThrow(() -> new RecursoNoEncontradoException("Colegio no encontrado"));
	}

	private static UsuarioAutenticado usuario() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
			return usuario;
		}
		throw new AccessDeniedException("Se requiere una Promotoría en sesión");
	}
}
