package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 10, tanda 3 (sección 3.10, como la nómina del sprint 9): Promotoría ve en «Para revisar» a quien exportó las
 * calificaciones de más de {@code max-secciones-dia} secciones en un día (una descarga masiva de datos de menores).
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasSiagie implements AlertasRevision {

	private final AuditoriaService auditoria;

	private final Clock reloj;

	private final int maxSeccionesDia;

	public AlertasSiagie(AuditoriaService auditoria, Clock reloj,
			@Value("${cuentasclaras.siagie.max-secciones-dia:10}") int maxSeccionesDia) {
		this.auditoria = auditoria;
		this.reloj = reloj;
		this.maxSeccionesDia = maxSeccionesDia;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		List<AlertaRevision> alertas = new ArrayList<>();
		auditoria.contarPorUsuarioDesde(AccionAuditoria.CALIFICACIONES_SIAGIE_EXPORTADAS,
				LocalDate.now(reloj).atStartOfDay()).forEach((quien, cuantas) -> {
					if (cuantas > maxSeccionesDia) {
						alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "SIAGIE", quien + " exportó hoy las "
								+ "calificaciones de " + cuantas + " secciones (más de " + maxSeccionesDia + "). Revisa por qué.",
								"/auditoria?accion=CALIFICACIONES_SIAGIE_EXPORTADAS"));
					}
				});
		return alertas;
	}
}
