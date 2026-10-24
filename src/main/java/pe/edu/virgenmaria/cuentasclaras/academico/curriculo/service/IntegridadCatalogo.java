package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 10, tanda 1 (M10): {@code sistema.academico} deja en la bitácora de cada colegio que la revisión diaria encontró
 * el catálogo distinto del jar (una vez por día). De ahí sale la alerta CRÍTICA {@code INTEGRIDAD_CATALOGO} a Promotoría
 * ({@link AlertasCatalogo}). Lo llama solo {@code academico.curriculo.proceso.RevisionCatalogo}.
 */
@Service
@PreAuthorize("hasRole('SISTEMA_ACADEMICO')")
public class IntegridadCatalogo {

	static final String ENTIDAD = "curriculo_version";

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public IntegridadCatalogo(AuditoriaService auditoria, Clock reloj) {
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/** @return si registró el evento (no lo repite el mismo día) */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean registrarDiferencias(List<String> diferencias) {
		if (diferencias.isEmpty()
				|| auditoria.contarDesde(AccionAuditoria.CATALOGO_NO_COINCIDE, LocalDate.now(reloj).atStartOfDay()) > 0) {
			return false;
		}
		String detalle = String.join(" ", diferencias);
		auditoria.registrar(AccionAuditoria.CATALOGO_NO_COINCIDE, ENTIDAD, "catalogo", null, null,
				"La revisión diaria del catálogo del currículo encontró diferencias con el del sistema: " + detalle
						+ " Ninguna nota se calcula con el catálogo cambiado sin que se vea: avisa al responsable técnico.");
		return true;
	}
}
