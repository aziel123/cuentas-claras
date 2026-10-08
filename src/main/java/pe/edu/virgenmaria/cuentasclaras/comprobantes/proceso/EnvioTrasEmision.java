package pe.edu.virgenmaria.cuentasclaras.comprobantes.proceso;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.ComprobanteEmitido;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.service.EnvioComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Envía el comprobante al OSE DESPUÉS del commit del pago, como {@code sistema.ose} y en su propia transacción
 * ({@link EnvioComprobantes}). Si el OSE falla, el pago no se revierte: queda PENDIENTE y lo retoma
 * {@link ReintentosComprobantes}.
 * <p>
 * Corre en un hilo aparte (dos como máximo): en {@code AFTER_COMMIT} el hilo del cobro todavía tiene su conexión y pedir
 * otra agotaba el pool con muchos cobros a la vez. Así, además, la cajera no espera al OSE. Con
 * {@code cuentasclaras.comprobantes.envio-sincrono: true} (perfil de pruebas) corre en el mismo hilo.
 */
@Component
public class EnvioTrasEmision {

	private static final Logger LOG = LoggerFactory.getLogger(EnvioTrasEmision.class);

	private static final int HILOS = 2;

	private final EnvioComprobantes envio;

	private final ExecutorService ejecutor;

	public EnvioTrasEmision(EnvioComprobantes envio,
			@Value("${cuentasclaras.comprobantes.envio-sincrono:false}") boolean sincrono) {
		this.envio = envio;
		this.ejecutor = sincrono ? null : hilos();
	}

	private static ExecutorService hilos() {
		AtomicInteger numero = new AtomicInteger();
		return Executors.newFixedThreadPool(HILOS, tarea -> {
			Thread hilo = new Thread(tarea, "envio-comprobantes-" + numero.incrementAndGet());
			hilo.setDaemon(true);
			return hilo;
		});
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alEmitir(ComprobanteEmitido evento) {
		if (ejecutor == null) {
			enviarEnSuColegio(evento);
			return;
		}
		try {
			ejecutor.execute(() -> enviarEnSuColegio(evento));
		}
		catch (RejectedExecutionException e) {
			LOG.warn("No se programó el envío del comprobante {}: queda PENDIENTE para los reintentos.", evento.id());
		}
	}

	void enviarEnSuColegio(ComprobanteEmitido evento) {
		try {
			EjecucionComoSistema.como(ActorSistema.OSE, evento.colegioId(), () -> envio.procesar(evento.id()));
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo enviar el comprobante {}: {}", evento.id(), e.getClass().getSimpleName());
		}
	}

	@PreDestroy
	void detener() {
		if (ejecutor != null) {
			ejecutor.shutdown();
		}
	}
}
