package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.Aviso;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 10, tanda 1 (M10): alerta CRÍTICA de «Para revisar» de Promotoría (y al celular) si en los últimos 7 días la
 * revisión diaria encontró el catálogo del currículo distinto del que trae el sistema.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
@Transactional(readOnly = true)
public class AlertasCatalogo implements AlertasRevision {

	static final String MODULO = "Académico";

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public AlertasCatalogo(AuditoriaService auditoria, Clock reloj) {
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>();
		auditoria.ultimaSecuenciaDesde(AccionAuditoria.CATALOGO_NO_COINCIDE, LocalDateTime.now(reloj).minusDays(7))
				.ifPresent(n -> alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "El catálogo del currículo de la "
						+ "base no coincide con el del sistema: alguien cambió una competencia o agregó una versión sin su "
						+ "migración. Avisa al responsable técnico.", "/auditoria?accion=CATALOGO_NO_COINCIDE",
						new Aviso(TipoAviso.INTEGRIDAD_CATALOGO, "CAT:" + n))));
		return alertas;
	}
}
