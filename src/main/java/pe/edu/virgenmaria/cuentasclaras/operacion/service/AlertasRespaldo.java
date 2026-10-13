package pe.edu.virgenmaria.cuentasclaras.operacion.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision.Gravedad;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertasRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.operacion.config.PropiedadesMonitoreo;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoRespaldo;
import pe.edu.virgenmaria.cuentasclaras.operacion.salud.EstadoTecnico;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 7, tanda 1 (decisión 93): «Sin respaldo» y «Faltan filas» también le llegan a Promotoría en «Para revisar»,
 * además del correo al operador. El respaldo es de toda la base, pero desde el sprint 8 (tanda 3, V30) «Faltan filas» es
 * por colegio: cada Promotoría ve y resuelve la de su colegio.
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTOR','SISTEMA_PANEL')")
public class AlertasRespaldo implements AlertasRevision {

	static final String MODULO = "Respaldos";

	private final EstadoTecnico estado;

	private final PropiedadesMonitoreo propiedades;

	public AlertasRespaldo(EstadoTecnico estado, PropiedadesMonitoreo propiedades) {
		this.estado = estado;
		this.propiedades = propiedades;
	}

	@Override
	public List<AlertaRevision> alertas() {
		// Sprint 8, tanda 3 (V30): «Faltan filas» es la de ESTE colegio (Promotoría o sistema.panel en su colegio).
		Long colegio = ContextoColegio.actual();
		EstadoRespaldo respaldo = estado.respaldo(colegio != null && colegio > 0 ? colegio : null);
		List<AlertaRevision> alertas = new ArrayList<>();
		if (respaldo.faltanFilas()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "Faltan filas que existían en un respaldo anterior. "
					+ "Avisa al responsable técnico y no toques nada: la alerta sigue hasta que la resuelvas con motivo en "
					+ "el estado técnico.", "/panel/sistema"));
		}
		if (propiedades.respaldoExigido() && !respaldo.alDia()) {
			alertas.add(new AlertaRevision(Gravedad.CRITICA, MODULO, "No hay un respaldo de las últimas "
					+ propiedades.respaldoMaxHoras() + " horas. Avisa al responsable técnico.", "/panel/sistema"));
		}
		return alertas;
	}
}
