package pe.edu.virgenmaria.cuentasclaras.exportacion.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.SuspensionAplicada;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.MotivoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.Exportador;

/**
 * Sprint 11, tanda 2 (sección 3.9): {@code sistema.exportacion} genera la exportación de un colegio al aplicarse su
 * suspensión (después de confirmarse), antes de su cierre o la de un pedido escrito que creó un operador. Tras el commit
 * corre en un hilo propio (sin la transacción ya cerrada de quien la pidió) y se espera a que termine.
 */
@Component
public class GeneracionExportaciones {

	private static final Logger LOG = LoggerFactory.getLogger(GeneracionExportaciones.class);

	private final Exportador exportador;

	public GeneracionExportaciones(Exportador exportador) {
		this.exportador = exportador;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void alSuspender(SuspensionAplicada suspension) {
		enHiloPropio(() -> exportar(suspension.colegioId(), MotivoExportacion.SUSPENSION, suspension.cambioEstadoId()));
	}

	/** Crea y genera la exportación (SUSPENSION o CIERRE): devuelve su id. */
	public Long exportar(long colegioId, MotivoExportacion motivo, Long cambioEstadoId) {
		return EjecucionComoSistema.como(ActorSistema.EXPORTACION, colegioId,
				() -> exportador.exportar(motivo, cambioEstadoId));
	}

	/** Genera (CSV, libretas, adjuntos, manifiesto y ZIP) una exportación ya creada. */
	public void generar(long colegioId, Long exportacionId) {
		EjecucionComoSistema.como(ActorSistema.EXPORTACION, colegioId, () -> exportador.generar(exportacionId));
	}

	private static void enHiloPropio(Runnable tarea) {
		Thread hilo = new Thread(() -> {
			try {
				tarea.run();
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo generar la exportación: {}", e.getMessage(), e);
			}
		}, "exportacion-colegio");
		hilo.start();
		try {
			hilo.join();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
