package pe.edu.virgenmaria.cuentasclaras.privacidad.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.privacidad.config.PropiedadesPrivacidad;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.EstadoAvisoColegio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Sprint 8, tanda 1 (sección 3.7 y paso 11 de la puesta en marcha): Dirección aprueba la versión vigente del aviso de
 * privacidad para SU colegio (el colegio es el responsable de los datos; el proveedor, el encargado). Queda en el colegio
 * (versión, quién y cuándo) y en la bitácora ({@code PRIVACIDAD_COLEGIO_APROBADA}, con la versión anterior y la nueva).
 * El colegio sale SIEMPRE de la sesión: nadie aprueba el aviso de otro colegio.
 */
@Service
public class AprobacionAvisoPrivacidad {

	private final ColegioRepository colegios;

	private final PropiedadesPrivacidad propiedades;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public AprobacionAvisoPrivacidad(ColegioRepository colegios, PropiedadesPrivacidad propiedades,
			AuditoriaService auditoria, Clock reloj) {
		this.colegios = colegios;
		this.propiedades = propiedades;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** El estado del aviso en el colegio de la sesión (lo ven quienes entran a /colegio). */
	@PreAuthorize("hasAnyRole('PROMOTOR','DIRECTOR','ADMINISTRACION')")
	@Transactional(readOnly = true)
	public EstadoAvisoColegio estado() {
		Colegio colegio = colegioDeLaSesion();
		return new EstadoAvisoColegio(propiedades.versionAviso(), colegio.getAvisoPrivacidadVersion(),
				colegio.getAvisoPrivacidadAprobadoPor(), colegio.getAvisoPrivacidadAprobadoEn());
	}

	/** Solo Dirección. Aprobar dos veces la misma versión no cambia nada (y no se registra dos veces). */
	@PreAuthorize("hasRole('DIRECTOR')")
	@Transactional
	public EstadoAvisoColegio aprobar() {
		Colegio colegio = colegioDeLaSesion();
		String vigente = propiedades.versionAviso();
		String anterior = colegio.getAvisoPrivacidadVersion();
		if (vigente.equals(anterior)) {
			throw new ReglaNegocioException("La versión " + vigente + " del aviso ya está aprobada en este colegio.");
		}
		String quien = usuarioActual();
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		colegio.aprobarAvisoPrivacidad(vigente, quien, ahora);
		colegios.save(colegio);
		auditoria.registrar(AccionAuditoria.PRIVACIDAD_COLEGIO_APROBADA, "colegio", colegio.getId().toString(), anterior,
				vigente, "Dirección aprobó la versión " + vigente + " del aviso de privacidad del colegio "
						+ colegio.getNombre() + (anterior == null ? "" : " (antes, la " + anterior + ")") + ".");
		return new EstadoAvisoColegio(vigente, vigente, quien, ahora);
	}

	private Colegio colegioDeLaSesion() {
		Long id = ContextoColegio.actual();
		if (id == null || id <= 0) {
			throw new RecursoNoEncontradoException("No hay un colegio en la sesión.");
		}
		return colegios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe el colegio."));
	}

	private static String usuarioActual() {
		Authentication a = SecurityContextHolder.getContext().getAuthentication();
		return a == null ? "" : a.getName();
	}
}
