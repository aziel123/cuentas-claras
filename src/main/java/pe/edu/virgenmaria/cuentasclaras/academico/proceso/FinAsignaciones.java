package pe.edu.virgenmaria.cuentasclaras.academico.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.service.AsignacionesDeSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;

import java.util.List;

/**
 * Sprint 9, tanda 1 (sección 3.2): cada madrugada, en cada colegio, {@code sistema.academico} termina las asignaciones
 * docentes de un año cerrado o de una cuenta que ya no es un docente activo. Primero el colegio y el actor, después la
 * transacción; cada asignación en la suya: si una falla, queda en el log y la pasada siguiente la retoma.
 */
@Component
public class FinAsignaciones {

	private static final Logger LOG = LoggerFactory.getLogger(FinAsignaciones.class);

	private final AsignacionesDeSistema asignaciones;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	public FinAsignaciones(AsignacionesDeSistema asignaciones, RecorridoColegios colegios,
			PlatformTransactionManager transacciones) {
		this.asignaciones = asignaciones;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Scheduled(cron = "${cuentasclaras.academico.fin-asignaciones-cron:0 20 2 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		for (Long colegio : colegios.activos()) {
			terminar(colegio);
		}
	}

	/** @return cuántas terminó en ese colegio */
	public int terminar(long colegioId) {
		List<Long> ids = EjecucionComoSistema.como(ActorSistema.ACADEMICO, colegioId,
				() -> transaccion.execute(t -> asignaciones.paraTerminar()));
		int hechas = 0;
		for (Long id : ids) {
			try {
				Boolean terminada = EjecucionComoSistema.como(ActorSistema.ACADEMICO, colegioId,
						() -> transaccion.execute(t -> asignaciones.terminarPorSistema(id)));
				if (Boolean.TRUE.equals(terminada)) {
					hechas++;
				}
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo terminar la asignación {} en el colegio {}: {} (se reintentará)", id, colegioId,
						e.getClass().getSimpleName());
			}
		}
		return hechas;
	}
}
