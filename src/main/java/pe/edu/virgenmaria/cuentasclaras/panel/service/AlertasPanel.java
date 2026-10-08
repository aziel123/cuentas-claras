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
 *   <li>CRÍTICA (P5): el resumen de hoy no salió pasada la {@code resumen-alerta-hora} (21:00), o el de un día anterior
 *       no salió. Correcciones del sprint 6 (S6-B1, QA-S6-1 y QA-S6-5): se revisa desde el último resumen CONFIRMADO, sin
 *       ventana (un sábado o el día antes de un feriado ya no se pierden, y la alerta no se apaga sola a los 35 días). Una
 *       alerta por día de los últimos {@value #DIAS_UNO_POR_UNO}; los anteriores, en una sola. Se activa con el primer
 *       resumen del colegio (antes no hay nada que suprimir).</li>
 *   <li>CRÍTICA (S6-M1): una foto del resumen que no guardó sistema.panel (plantada por fuera de la aplicación).</li>
 *   <li>ATENCIÓN con aviso al celular (S6-M2): una familia de la llamada de control no contestó dos veces y se reemplazó;
 *       Dirección registró una llamada con la semana delegada (no se avisa a quien la registró).</li>
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

	/** S6-B1: los días sin resumen más recientes salen uno por uno; los anteriores, juntos. */
	static final int DIAS_UNO_POR_UNO = 7;

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
		for (pe.edu.virgenmaria.cuentasclaras.panel.model.ResumenDiario foto : resumenes.suplantadas()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "La foto del resumen del " + foto.getFecha().format(FECHA)
					+ " no la guardó el sistema (no está en la bitácora): alguien la insertó por fuera de la aplicación y el "
					+ "resumen verdadero de ese día no salió. Revisa los pagos de ese día y quién tiene acceso a la base.",
					"/panel/resumenes", new Aviso(TipoAviso.RESUMEN_SUPLANTADO, "RF:" + foto.getId(),
							foto.getFecha().format(FECHA))));
		}
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
		for (LlamadaControl l : llamadas.sinRespuestaRecientes()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Llamada de control de la semana del "
					+ l.getSemana().format(FECHA) + ": la familia (código " + l.getFamiliaId() + ") no contestó dos veces y "
					+ "se eligió otra en su lugar. Si vuelve a pasar con la misma familia, visítala o cítala.",
					"/panel/llamadas", new Aviso(TipoAviso.LLAMADA_REEMPLAZADA, "LR:" + l.getId(),
							l.getSemana().format(FECHA))));
		}
		for (LlamadaControl l : llamadas.registradasPorDireccion()) {
			alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, l.getCreadoPor() + " (Dirección) registró «"
					+ l.getResultado().etiqueta() + "» en la llamada de control a la familia (código " + l.getFamiliaId()
					+ ") de la semana del " + l.getSemana().format(FECHA) + ". Confírmalo si te parece raro.",
					"/panel/llamadas", new Aviso(TipoAviso.LLAMADA_POR_DIRECCION, "LD:" + l.getId(),
							l.getSemana().format(FECHA), java.util.Set.of(l.getCreadoPor()))));
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

	/**
	 * P5 con S6-B1, QA-S6-1 y QA-S6-5: cada día que debía tener resumen desde el último resumen confirmado (hoy, solo
	 * pasada la hora de la alerta). Así el aviso del sábado o del día antes de un feriado sale en la primera pasada del
	 * siguiente día de mensajes, y un resumen que dejó de salir hace más de 35 días sigue alertando.
	 */
	private void resumenNoSalio(List<AlertaRevision> alertas, LocalDateTime ahora) {
		LocalDate hoy = ahora.toLocalDate();
		LocalDate hasta = ahora.toLocalTime().isBefore(propiedades.resumenAlertaHora()) ? hoy.minusDays(1) : hoy;
		List<LocalDate> dias = resumenes.diasSinResumen(hasta);
		if (dias.isEmpty()) {
			return;
		}
		List<LocalDate> anteriores = dias.subList(0, Math.max(0, dias.size() - DIAS_UNO_POR_UNO));
		if (!anteriores.isEmpty()) {
			LocalDate primero = anteriores.getFirst();
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Desde el " + primero.format(FECHA) + " el resumen "
					+ "diario no sale (" + dias.size() + " días sin resumen confirmado). Revisa el panel y la bandeja de "
					+ "envíos.", "/panel/resumenes", new Aviso(TipoAviso.RESUMEN_NO_SALIO, "RSD:" + primero,
							primero.format(FECHA))));
		}
		for (LocalDate dia : dias.subList(anteriores.size(), dias.size())) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "El resumen del " + dia.format(FECHA) + " no salió "
					+ "a todas las personas de Promotoría (o no se generó). Si no te llegó, revisa el panel y la "
					+ "bandeja de envíos.", "/panel/resumenes",
					new Aviso(TipoAviso.RESUMEN_NO_SALIO, "RS:" + dia, dia.format(FECHA))));
		}
	}
}
