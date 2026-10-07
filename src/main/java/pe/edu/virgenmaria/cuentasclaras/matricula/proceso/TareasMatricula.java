package pe.edu.virgenmaria.cuentasclaras.matricula.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Corre el trabajo de {@code sistema.matricula} en un colegio: primero el colegio y el actor, después la transacción
 * (nueva, aunque se llame después del commit de otra). Cada elemento va en su propia transacción: si uno falla, queda
 * en el log y el barrido siguiente lo retoma; los demás siguen.
 */
final class TareasMatricula {

	private static final Logger LOG = LoggerFactory.getLogger(TareasMatricula.class);

	private final TransactionTemplate transaccion;

	TareasMatricula(PlatformTransactionManager transacciones) {
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	<T> T leer(long colegioId, Supplier<T> lectura) {
		return EjecucionComoSistema.como(ActorSistema.MATRICULA, colegioId,
				() -> transaccion.execute(estado -> lectura.get()));
	}

	/** @return cuántos se procesaron sin error */
	int cadaUno(long colegioId, List<Long> ids, Consumer<Long> tarea, String que) {
		int hechos = 0;
		for (Long id : ids) {
			try {
				EjecucionComoSistema.como(ActorSistema.MATRICULA, colegioId,
						() -> transaccion.executeWithoutResult(estado -> tarea.accept(id)));
				hechos++;
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo {} {} en el colegio {}: {} (se reintentará)", que, id, colegioId,
						e.getClass().getSimpleName());
			}
		}
		return hechos;
	}
}
