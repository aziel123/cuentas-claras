package pe.edu.virgenmaria.cuentasclaras.matricula.proceso;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.virgenmaria.cuentasclaras.caja.service.PagoRegistrado;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.matricula.service.ProcesosMatricula;

/**
 * Sprint 5, tanda 2 (G18): después del commit de un pago (en caja, en línea o por el banco), {@code sistema.matricula}
 * activa toda matrícula RESERVADA cuya cuota de matrícula quedó PAGADA o EXONERADA; al activarse, cobranza genera las
 * pensiones. Nadie la activa a mano (en MySQL, el trigger exige la matrícula pagada y al actor). Un barrido cada 10
 * minutos retoma lo que haya faltado y retira las reservadas cuya cuota de matrícula se anuló con aprobación
 * (desistimiento). Si después se anula el pago, la matrícula NO se desactiva sola: queda una alerta (decisión 57).
 */
@Component
public class ActivacionMatriculas {

	private final ProcesosMatricula procesos;

	private final RecorridoColegios colegios;

	private final TareasMatricula tareas;

	public ActivacionMatriculas(ProcesosMatricula procesos, RecorridoColegios colegios,
			PlatformTransactionManager transacciones) {
		this.procesos = procesos;
		this.colegios = colegios;
		this.tareas = new TareasMatricula(transacciones);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alRegistrarPago(PagoRegistrado evento) {
		Long colegio = ContextoColegio.actual();
		if (colegio == null || colegio <= 0) {
			return;
		}
		activar(colegio);
	}

	@Scheduled(fixedDelayString = "${cuentasclaras.matricula.barrido-cada:10m}", initialDelayString = "120s")
	public void barrer() {
		for (Long colegio : colegios.activos()) {
			activar(colegio);
			desistir(colegio);
		}
	}

	/** @return cuántas matrículas se activaron */
	public int activar(Long colegioId) {
		return tareas.cadaUno(colegioId, tareas.leer(colegioId, procesos::paraActivar), procesos::activar,
				"activar la matrícula");
	}

	/** @return cuántas reservadas desistidas se retiraron */
	public int desistir(Long colegioId) {
		return tareas.cadaUno(colegioId, tareas.leer(colegioId, procesos::desistidas), procesos::desistir,
				"retirar la matrícula desistida");
	}
}
