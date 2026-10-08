package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CierreMensualBanco;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.CuentaBancaria;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoCierreMensual;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.CierreMensualBancoRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.CuentaBancariaRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Alertas del cierre bancario mensual (sprint 5, tanda 3) para «Para revisar» de Promotoría:
 * <ul>
 *   <li>CRÍTICA: un cierre en DISCREPANCIA (el estado de cuenta oficial no coincide con los extractos confirmados).</li>
 *   <li>ATENCIÓN: un cierre sin hacer pasado el día {@value #DIA_LIMITE}; el mes anterior sin extractos confirmados que
 *       lo cubran completo.</li>
 * </ul>
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
@Transactional(readOnly = true)
public class AlertasCierreMensual implements AlertasRevision {

	static final int DIA_LIMITE = 10;

	private static final String MODULO = "Conciliación";

	private static final String ENLACE = "/conciliacion/cierres-mensuales";

	private final CierreMensualBancoRepository cierres;

	private final CuentaBancariaRepository cuentas;

	private final Clock reloj;

	public AlertasCierreMensual(CierreMensualBancoRepository cierres, CuentaBancariaRepository cuentas, Clock reloj) {
		this.cierres = cierres;
		this.cuentas = cuentas;
		this.reloj = reloj;
	}

	@Override
	public List<AlertaRevision> alertas() {
		LocalDate hoy = LocalDate.now(reloj);
		List<AlertaRevision> alertas = new ArrayList<>();
		for (CierreMensualBanco c : cierres.findByEstadoOrderByAnioAscMesAsc(EstadoCierreMensual.DISCREPANCIA)) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "El cierre bancario de "
					+ ServicioCierreMensual.nombre(c.periodo()) + " (" + c.getCuenta().descripcion() + ") quedó en "
					+ "DISCREPANCIA: el estado de cuenta oficial no coincide con los extractos confirmados. Revísalo con el "
					+ "contador.", ENLACE + "/" + c.getId()));
		}
		for (CierreMensualBanco c : cierres.findByEstadoOrderByAnioAscMesAsc(EstadoCierreMensual.ABIERTO)) {
			LocalDate limite = c.periodo().plusMonths(1).atDay(DIA_LIMITE);
			if (hoy.isAfter(limite)) {
				alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "El cierre bancario de "
						+ ServicioCierreMensual.nombre(c.periodo()) + " sigue sin hacerse (vencía el día " + DIA_LIMITE
						+ "). Lo hace a ciegas quien no subió ni confirmó extractos del mes.", ENLACE + "/" + c.getId()));
			}
		}
		YearMonth anterior = YearMonth.from(hoy).minusMonths(1);
		if (anterior.getYear() >= 2026 && hoy.getDayOfMonth() >= 2) {
			for (CuentaBancaria cuenta : cuentas.findByActivaTrueOrderByIdAsc()) {
				boolean cuentaDelMes = cuenta.getCreadoEn() != null
						&& !cuenta.getCreadoEn().toLocalDate().isAfter(anterior.atDay(1));
				if (cuentaDelMes && !cierres.existsByCuentaIdAndAnioAndMes(cuenta.getId(), anterior.getYear(),
						anterior.getMonthValue())) {
					alertas.add(new AlertaRevision(Gravedad.ATENCION, MODULO, "Los extractos confirmados de "
							+ cuenta.descripcion() + " no cubren todo " + ServicioCierreMensual.nombre(anterior)
							+ ": falta subir o confirmar alguno para hacer el cierre del mes.", "/conciliacion/extractos"));
				}
			}
		}
		return alertas;
	}
}
