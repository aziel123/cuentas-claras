package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sprint 9, tanda 3 (sección 12, B10 y B11): lo de comunicados y de la nómina que Promotoría debe revisar. Quien vigila no
 * es quien actúa: los retiros de comunicados y la nómina masiva los hace Dirección, y las alertas van a Promotoría.
 * <ul>
 *   <li>Comunicados retirados en los últimos 7 días (E28).</li>
 *   <li>Comunicados cuyos destinatarios no coincidieron con su alcance (B11).</li>
 *   <li>Intentos de enviar un comunicado de cobranza o de pagos (el filtro los rechazó), con el docente si fue uno.</li>
 *   <li>Más de {@value #MAX_SECCIONES_NOMINA_DIA} secciones de la nómina del SIAGIE exportadas hoy por una persona.</li>
 * </ul>
 * Solo cuenta eventos de la bitácora del colegio: sin nombres de alumnos ni textos.
 */
@Component
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasComunicados implements AlertasRevision {

	static final int MAX_SECCIONES_NOMINA_DIA = 10;

	private final AuditoriaService auditoria;

	private final Clock reloj;

	public AlertasComunicados(AuditoriaService auditoria, Clock reloj) {
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AlertaRevision> alertas() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDateTime semana = ahora.minusDays(7);
		List<AlertaRevision> alertas = new ArrayList<>();
		long retirados = auditoria.contarDesde(AccionAuditoria.COMUNICADO_RETIRADO, semana);
		if (retirados > 0) {
			alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "Comunicados", retirados
					+ " comunicado(s) retirado(s) en los últimos 7 días: revisa el motivo en la bitácora.", "/comunicados",
					null));
		}
		long descuadrados = auditoria.contarDesde(AccionAuditoria.COMUNICADO_DESTINATARIOS_DESCUADRADOS, semana);
		if (descuadrados > 0) {
			alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "Comunicados", descuadrados
					+ " comunicado(s) llegaron a familias que no coinciden con su alcance: revísalo con el responsable "
					+ "técnico.", "/comunicados", null));
		}
		long rechazados = auditoria.contarDesde(AccionAuditoria.COMUNICADO_RECHAZADO_POR_CONTENIDO, semana);
		if (rechazados > 0) {
			alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "Comunicados", rechazados
					+ " intento(s) de enviar un comunicado que el filtro rechazó (cobranza, pagos, contactos o enlaces) "
					+ "en los últimos 7 días.", "/auditoria", null));
		}
		Map<String, Long> nominas = auditoria.contarPorUsuarioDesde(AccionAuditoria.NOMINA_SIAGIE_EXPORTADA,
				LocalDate.now(reloj).atStartOfDay());
		nominas.forEach((quien, cuantas) -> {
			if (cuantas > MAX_SECCIONES_NOMINA_DIA) {
				alertas.add(new AlertaRevision(AlertaRevision.Gravedad.ATENCION, "Nómina SIAGIE", quien + " exportó hoy "
						+ cuantas + " secciones de la nómina (DNI y fechas de nacimiento de menores).", "/auditoria", null));
			}
		});
		return alertas;
	}
}
