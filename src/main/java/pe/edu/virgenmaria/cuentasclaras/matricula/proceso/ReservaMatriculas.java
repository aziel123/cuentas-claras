package pe.edu.virgenmaria.cuentasclaras.matricula.proceso;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ProcesosMatricula;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.RenovacionConfirmada;

import java.util.List;

/**
 * Sprint 5, tanda 2: después del commit de una renovación confirmada, {@code sistema.matricula} reserva la matrícula del
 * año siguiente (con su cuota de matrícula del plan aprobado) y la renovación pasa a MATRICULADA. Un barrido cada 10
 * minutos retoma las confirmadas que quedaron sin reservar.
 */
@Component
public class ReservaMatriculas {

	private final ProcesosMatricula procesos;

	private final RecorridoColegios colegios;

	private final TareasMatricula tareas;

	public ReservaMatriculas(ProcesosMatricula procesos, RecorridoColegios colegios,
			PlatformTransactionManager transacciones) {
		this.procesos = procesos;
		this.colegios = colegios;
		this.tareas = new TareasMatricula(transacciones);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alConfirmarse(RenovacionConfirmada evento) {
		tareas.cadaUno(evento.colegioId(), List.of(evento.renovacionId()), procesos::reservar, "reservar la renovación");
	}

	@Scheduled(fixedDelayString = "${cuentasclaras.matricula.barrido-cada:10m}", initialDelayString = "90s")
	public void barrer() {
		colegios.activos().forEach(this::enColegio);
	}

	/** Reserva las confirmadas pendientes de un colegio (la tarea programada; las pruebas, directamente). */
	public int enColegio(Long colegioId) {
		return tareas.cadaUno(colegioId, tareas.leer(colegioId, procesos::confirmadasSinMatricula), procesos::reservar,
				"reservar la renovación");
	}
}
