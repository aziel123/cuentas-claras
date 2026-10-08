package pe.edu.virgenmaria.cuentasclaras.auditoria.service;

import org.springframework.beans.factory.ObjectProvider;
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

	private final ObjectProvider<EnviosHuella> envios;

	public AlertasHuella(HuellaGuardadaRepository huellas, AuditoriaService auditoria, Clock reloj,
			ObjectProvider<EnviosHuella> envios) {
		this.huellas = huellas;
		this.auditoria = auditoria;
		this.reloj = reloj;
		this.envios = envios;
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
		if (auditoria.contarDesde(AccionAuditoria.HUELLA_RETROCEDIO, ahora.minusDays(7)) > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La bitácora retrocedió: su último evento es anterior "
					+ "a una huella que ya se guardó o que recibiste. Alguien la recortó. Compárala con tus mensajes.",
					"/auditoria?accion=HUELLA_RETROCEDIO"));
		}
		if (auditoria.contarDesde(AccionAuditoria.HUELLA_FALTAN_DIAS, ahora.minusDays(7)) > 0) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Faltan huellas diarias de la bitácora de uno o más "
					+ "días. Compara con los mensajes que recibiste.", "/auditoria?accion=HUELLA_FALTAN_DIAS"));
		}
		LocalDate ayer = ahora.toLocalDate().minusDays(1);
		EnviosHuella enviadas = envios.getIfAvailable();
		boolean huboHuellas = auditoria.contarDesde(AccionAuditoria.HUELLA_ENVIADA, ahora.minusDays(30)) > 0
				|| (enviadas != null && enviadas.mayorEnviada().isPresent());
		var deAyer = huellas.findByFecha(ayer);
		if (ahora.toLocalTime().isAfter(LocalTime.of(7, 0)) && deAyer.isEmpty() && huboHuellas) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La huella de la bitácora de ayer no se generó a las "
					+ "07:00. Avisa al responsable técnico.", "/auditoria"));
		}
		// S5-B3: guardarla no basta; lo que protege es la copia en el celular de Promotoría.
		if (ahora.toLocalTime().isAfter(LocalTime.of(7, 0)) && deAyer.isPresent() && enviadas != null
				&& !enviadas.salio(deAyer.get().getId())) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La huella de la bitácora de ayer se guardó pero su "
					+ "mensaje no salió a Promotoría. Revisa la bandeja de envíos.", "/mensajes"));
		}
		return alertas;
	}
}
