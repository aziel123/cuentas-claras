package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.core.annotation.Order;
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
import pe.edu.virgenmaria.cuentasclaras.panel.config.PropiedadesPanel;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.AvanceLlamadas;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.ComparacionResumen;
import pe.edu.virgenmaria.cuentasclaras.panel.model.LlamadaControl;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Alertas del panel para «Para revisar» (sprint 6, tanda 2, sección 13). Se calculan al consultar:
 * <ul>
 *   <li>CRÍTICA (P5): el resumen de hoy no salió pasada la {@code resumen-alerta-hora} (21:00), o el de ayer no salió;
 *       se activa con el primer resumen del colegio (antes no hay nada que suprimir).</li>
 *   <li>CRÍTICA (P4): las cifras de un día ya informado cambiaron y no lo explica ninguna anulación aprobada ni pago
 *       tardío (un pago borrado o alterado por SQL). Si lo explican, PARA SABER.</li>
 *   <li>ATENCIÓN: alguien descargó más de {@code exportaciones-atencion} reportes hoy; el celular o el correo de alguien
 *       del personal cambió en los últimos 7 días. PARA SABER: las descargas de hoy.</li>
 *   <li>Tanda 3 (P17): CRÍTICA, una familia de la llamada de control no confirmó lo registrado (30 días, con aviso al
 *       celular); ATENCIÓN, el sábado y el domingo, si faltan llamadas de la semana.</li>
 * </ul>
 * No dependen de la persona en sesión: también las calcula {@code sistema.panel} (resumen y avisos al celular).
 */
@Service
@Order(50)
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasPanel implements AlertasRevision {

	static final String MODULO = "Panel";

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ResumenesDiarios resumenes;

	private final AuditoriaService auditoria;

	private final PropiedadesPanel propiedades;

	private final Clock reloj;

	private final LlamadasControl llamadas;

	public AlertasPanel(ResumenesDiarios resumenes, AuditoriaService auditoria, PropiedadesPanel propiedades,
			Clock reloj, LlamadasControl llamadas) {
		this.resumenes = resumenes;
		this.auditoria = auditoria;
		this.propiedades = propiedades;
		this.reloj = reloj;
		this.llamadas = llamadas;
	}

	@Override
	public List<AlertaRevision> alertas() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		LocalDate hoy = ahora.toLocalDate();
		List<AlertaRevision> alertas = new ArrayList<>();
		resumenNoSalio(alertas, ahora);
		for (ComparacionResumen c : resumenes.comparar()) {
			if (c.sinExplicar()) {
				alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Las cifras del " + c.fecha().format(FECHA)
						+ " cambiaron después de enviarse y no lo explica ninguna anulación ni pago tardío. "
						+ c.explicacion() + " Revisa la bitácora y los pagos de ese día.", "/panel/resumenes",
						new Aviso(TipoAviso.CIFRAS_CAMBIARON, "RD:" + c.resumenId(), c.fecha().format(FECHA))));
			}
			else if (c.cambio()) {
				alertas.add(new AlertaRevision(Gravedad.INFORMATIVA, MODULO, "Las cifras del " + c.fecha().format(FECHA)
						+ " cambiaron después de enviarse: " + c.explicacion(), "/panel/resumenes"));
			}
		}
		Map<String, Long> descargas = auditoria.contarPorUsuarioDesde(AccionAuditoria.REPORTE_EXPORTADO,
				hoy.atStartOfDay());
		descargas.forEach((quien, cuantas) -> {
			if (cuantas > propiedades.exportacionesAtencion()) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, quien + " descargó " + cuantas + " reportes en "
						+ "Excel hoy. Confirma que los necesitaba.", "/auditoria?accion=REPORTE_EXPORTADO"));
			}
		});
		if (!descargas.isEmpty()) {
			alertas.add(new AlertaRevision(Gravedad.INFORMATIVA, MODULO, "Descargas de Excel hoy: " + descargas.entrySet()
					.stream().map(e -> e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining(", ")) + ".",
					"/auditoria?accion=REPORTE_EXPORTADO"));
		}
		long contactos = auditoria.contarDesde(AccionAuditoria.CONTACTO_PERSONAL_CAMBIADO, ahora.minusDays(7));
		if (contactos > 0) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, contactos + " cambio(s) de celular o correo del "
					+ "personal en los últimos 7 días. Confirma con cada persona que el nuevo es suyo.",
					"/auditoria?accion=CONTACTO_PERSONAL_CAMBIADO"));
		}
		llamadasDeControl(alertas, hoy);
		return alertas;
	}

	/**
	 * Tanda 3 (P17): cada «No confirma» de los últimos 30 días es CRÍTICA (sale al celular una vez, con texto fijo: el
	 * de la alerta lleva la nota de quien llamó); el sábado y el domingo, si faltan llamadas de la semana, ATENCIÓN.
	 */
	private void llamadasDeControl(List<AlertaRevision> alertas, LocalDate hoy) {
		for (LlamadaControl l : llamadas.noConfirmanRecientes()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Llamada de control de la semana del "
					+ l.getSemana().format(FECHA) + ": la familia (código " + l.getFamiliaId() + ") no confirma lo "
					+ "registrado. Anotó " + l.getCreadoPor() + ": «" + l.getNota() + "». Revisa sus pagos y la caja de quien "
					+ "los cobró.", "/alumnos/familias/" + l.getFamiliaId(),
					new Aviso(TipoAviso.LLAMADA_NO_CONFIRMA, "LC:" + l.getId(), l.getSemana().format(FECHA))));
		}
		if (hoy.getDayOfWeek() == DayOfWeek.SATURDAY || hoy.getDayOfWeek() == DayOfWeek.SUNDAY) {
			AvanceLlamadas avance = llamadas.avance();
			if (avance.faltan() > 0) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Faltan " + avance.faltan() + " de "
						+ avance.esperadas() + " llamadas de control de esta semana. Hazlas antes de que termine el domingo.",
						"/panel/llamadas"));
			}
		}
	}

	/** P5: hoy pasada la hora de la alerta, y ayer todo el día (así el aviso sale también a las 07:00). */
	private void resumenNoSalio(List<AlertaRevision> alertas, LocalDateTime ahora) {
		if (!resumenes.huboResumenes()) {
			return;
		}
		LocalDate hoy = ahora.toLocalDate();
		List<LocalDate> dias = new ArrayList<>();
		dias.add(hoy.minusDays(1));
		if (!ahora.toLocalTime().isBefore(propiedades.resumenAlertaHora())) {
			dias.add(hoy);
		}
		for (LocalDate dia : dias) {
			if (resumenes.debiaSalir(dia) && !resumenes.salioATodos(dia)) {
				alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "El resumen del " + dia.format(FECHA) + " no salió "
						+ "a todas las personas de Promotoría (o no se generó). Si no te llegó, revisa el panel y la "
						+ "bandeja de envíos.", "/panel/resumenes",
						new Aviso(TipoAviso.RESUMEN_NO_SALIO, "RS:" + dia, dia.format(FECHA))));
			}
		}
	}
}
