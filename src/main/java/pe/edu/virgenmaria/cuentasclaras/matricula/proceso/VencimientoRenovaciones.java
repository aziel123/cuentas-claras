package pe.edu.virgenmaria.cuentasclaras.matricula.proceso;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ProcesosMatricula;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Sprint 5, tanda 2: cada día a las 00:30 (Lima), las propuestas de renovación sin respuesta pasada su fecha límite
 * vencen. Sin confirmación no hay deuda (decisión 53).
 */
@Component
public class VencimientoRenovaciones {

	private final ProcesosMatricula procesos;

	private final RecorridoColegios colegios;

	private final TareasMatricula tareas;

	private final Clock reloj;

	public VencimientoRenovaciones(ProcesosMatricula procesos, RecorridoColegios colegios,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.procesos = procesos;
		this.colegios = colegios;
		this.tareas = new TareasMatricula(transacciones);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.matricula.vencimiento-cron:0 30 0 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		LocalDate hoy = LocalDate.now(reloj);
		colegios.activos().forEach(c -> enColegio(c, hoy));
	}

	public int enColegio(Long colegioId, LocalDate hoy) {
		return tareas.leer(colegioId, () -> procesos.vencer(hoy));
	}
}
