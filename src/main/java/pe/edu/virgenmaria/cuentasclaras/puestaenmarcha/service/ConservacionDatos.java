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
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.AvisoPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.FirmaOperacionRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.PrimeraPromotoriaRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Sprint 11, tanda 2 (AP-11 y AP2-9): la autorización expresa del titular (la cuenta de la primera Promotoría) para que la
 * plataforma conserve los datos del colegio 12 meses desde que se aplica una suspensión (si no, se suprimen 30 días después
 * del cierre). Es el paso 16 de la Puesta en marcha, obligatorio. La da o la retira SOLO el titular, con la firma de su
 * sesión ({@code colegio:{id}:CONSERVACION} y {@code :CONSERVACION_RETIRADA}, una vez cada una), mientras el colegio opera;
 * desde SUSPENDIDO queda congelada. Cada cambio avisa al correo de control. La base lo exige igual (trg_colegio_cambio).
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
@Transactional
public class ConservacionDatos {

	// La vista la ven también Dirección y Administración (la Puesta en marcha les muestra el paso); firmar, solo el titular.

	/** Lo que ve el titular en la pantalla. */
	public record Vista(boolean esTitular, boolean autorizada, String autorizadaPor, LocalDateTime autorizadaEn,
			boolean editable, boolean puedeAutorizar, boolean puedeRetirar) {
	}

	private final ColegioRepository colegios;

	private final PrimeraPromotoriaRepository primeras;

	private final FirmaSesion firma;

	private final FirmaOperacionRepository firmas;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final Clock reloj;

	public ConservacionDatos(ColegioRepository colegios, PrimeraPromotoriaRepository primeras, FirmaSesion firma,
			FirmaOperacionRepository firmas, AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock reloj) {
		this.colegios = colegios;
		this.primeras = primeras;
		this.firma = firma;
		this.firmas = firmas;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	@PreAuthorize("hasAnyRole('PROMOTOR', 'DIRECTOR', 'ADMINISTRACION')")
	public Vista vista() {
		Colegio c = colegio();
		boolean titular = esTitular();
		boolean editable = c.getEstado().opera();
		return new Vista(titular, c.conservacionAutorizada(), c.getConservacionAutorizadaPor(),
				c.getConservacionAutorizadaEn(), editable,
				titular && editable && !c.conservacionAutorizada() && !firmas.existsByClave(ClaveFirma.conservacion(c.getId())),
				titular && editable && c.conservacionAutorizada()
						&& !firmas.existsByClave(ClaveFirma.conservacionRetirada(c.getId())));
	}

	public void autorizar() {
		Colegio c = colegio();
		UsuarioAutenticado titular = titular();
		String clave = ClaveFirma.conservacion(c.getId());
		if (firmas.existsByClave(clave)) {
			throw new ReglaNegocioException("La autorización de conservación ya se firmó una vez en este colegio: para "
					+ "volver a darla, pídelo a la plataforma por escrito.");
		}
		c.autorizarConservacion(titular.getUsername(), LocalDateTime.now(reloj));
		firma.firmar(clave);
		colegios.saveAndFlush(c);
		auditoria.registrar(AccionAuditoria.CONSERVACION_AUTORIZADA, "colegio", c.getId().toString(), null, "AUTORIZADA",
				titular.nombreCompleto() + ", titular del colegio, autorizó con su firma que la plataforma conserve los "
						+ "datos 12 meses desde que se aplique una suspensión (las familias consultan hasta entonces). Se "
						+ "avisó al correo de control.");
		avisar("el titular autorizó conservar los datos", "12 meses desde una suspensión", "AUTORIZADA");
	}

	public void retirar() {
		Colegio c = colegio();
		UsuarioAutenticado titular = titular();
		String clave = ClaveFirma.conservacionRetirada(c.getId());
		if (firmas.existsByClave(clave)) {
			throw new ReglaNegocioException("La autorización ya se retiró una vez en este colegio.");
		}
		c.retirarConservacion();
		firma.firmar(clave);
		colegios.saveAndFlush(c);
		auditoria.registrar(AccionAuditoria.CONSERVACION_RETIRADA, "colegio", c.getId().toString(), "AUTORIZADA", null,
				titular.nombreCompleto() + ", titular del colegio, retiró con su firma la autorización de conservación: "
						+ "al cerrar, los datos se suprimen 30 días después. Se avisó al correo de control.");
		avisar("el titular retiró la autorización de conservar los datos", "se suprimen 30 días después del cierre",
				"RETIRADA");
	}

	private void avisar(String que, String detalle, String sufijo) {
		Long colegioId = ContextoColegio.actual();
		Long titularId = primeras.findFirstByIdNotNull().map(p -> p.getUsuarioId()).orElse(null);
		eventos.publishEvent(new AvisoPlataforma(colegioId, AvisoPlataforma.Tipo.CONSERVACION, List.of(que, detalle),
				"colegio", colegioId, sufijo, titularId, true, true, true));
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
