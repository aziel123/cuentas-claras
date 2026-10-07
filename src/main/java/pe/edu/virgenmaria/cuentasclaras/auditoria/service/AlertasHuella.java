package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.repository.HuellaGuardadaRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alertas de la huella diaria (sprint 5, G13) para «Para revisar» de Promotoría: CRÍTICA si una huella guardada ya no
 * coincide con la bitácora (recorte o alteración) en los últimos 7 días, o si a las 07:00 no se generó la de ayer.
 */
@Service
@PreAuthorize("hasRole('PROMOTOR')")
@Transactional(readOnly = true)
public class AlertasHuella implements AlertasRevision {

	static final String MODULO = "Auditoría";

	private final HuellaGuardadaRepository huellas;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public AlertasHuella(HuellaGuardadaRepository huellas, AuditoriaService auditoria, Clock reloj) {
		this.huellas = huellas;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	public List<AlertaRevision> alertas() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		List<AlertaRevision> alertas = new ArrayList<>();
		if (auditoria.contarDesde(AccionAuditoria.HUELLA_NO_COINCIDE, ahora.minusDays(7)) > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La bitácora no coincide con una huella guardada: "
					+ "alguien la recortó o la alteró. Compárala con la huella que recibiste en tu celular.",
					"/auditoria?accion=HUELLA_NO_COINCIDE"));
		}
		LocalDate ayer = ahora.toLocalDate().minusDays(1);
		if (ahora.toLocalTime().isAfter(LocalTime.of(7, 0)) && huellas.findByFecha(ayer).isEmpty()
				&& auditoria.contarDesde(AccionAuditoria.HUELLA_ENVIADA, ahora.minusDays(30)) > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La huella de la bitácora de ayer no se generó a las "
					+ "07:00. Avisa al responsable técnico.", "/auditoria"));
		}
		return alertas;
	}
}
