package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.EstadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ResultadoEnvio;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.repository.ComprobanteRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Envía el comprobante al OSE DESPUÉS del commit del pago, en su propia transacción. Si el OSE falla, el pago NO se
 * revierte: el comprobante queda PENDIENTE con un intento más (los reintentos programados llegan en el sprint 4).
 * <p>
 * El envío corre en un hilo aparte (dos como máximo): en {@code AFTER_COMMIT} el hilo del cobro todavía tiene su
 * conexión y pedir otra para una transacción nueva agotaba el pool con muchos cobros a la vez (lo detectó la prueba de
 * 20 cobros concurrentes). Así, además, la cajera no espera al OSE. Nunca deja escapar una excepción. El log no lleva
 * datos personales: solo la serie y el número.
 * <p>
 * Con {@code cuentasclaras.comprobantes.envio-sincrono: true} (perfil de pruebas, hallazgo 13 de QA) el envío corre en
 * el mismo hilo, en una transacción nueva: no quedan hilos vivos que choquen con la limpieza de la prueba siguiente.
 * Un comprobante que ya no está PENDIENTE no se vuelve a enviar (su resultado no cambia; en MySQL lo exige un trigger).
 */
@Component
public class EnvioComprobantes {

	private static final Logger LOG = LoggerFactory.getLogger(EnvioComprobantes.class);

	static final String SIN_RESPUESTA = "Sin respuesta del OSE: se reintentará.";

	private static final int HILOS = 2;

	private final ComprobanteRepository comprobantes;

	private final EmisorElectronico emisor;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final ExecutorService ejecutor;

	public EnvioComprobantes(ComprobanteRepository comprobantes, EmisorElectronico emisor,
			PlatformTransactionManager transacciones, Clock reloj,
			@Value("${cuentasclaras.comprobantes.envio-sincrono:false}") boolean sincrono) {
		this.comprobantes = comprobantes;
		this.emisor = emisor;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
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
			LOG.warn("No se programó el envío del comprobante {}: queda PENDIENTE.", evento.id());
		}
	}

	void enviarEnSuColegio(ComprobanteEmitido evento) {
		try {
			Runnable envio = () -> transaccion.executeWithoutResult(estado -> enviar(evento.id()));
			if (ejecutor == null && evento.colegioId().equals(ContextoColegio.actual())) {
				// Síncrono, en el mismo hilo y colegio del cobro (aún dentro de su AFTER_COMMIT): transacción nueva.
				envio.run();
			}
			else {
				ContextoColegio.en(evento.colegioId(), envio);
			}
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo registrar el envío del comprobante {}: {}", evento.id(), e.getClass().getSimpleName());
		}
	}

	private void enviar(Long id) {
		Comprobante comprobante = comprobantes.findById(id).orElse(null);
		if (comprobante == null) {
			LOG.error("El comprobante {} no existe en su colegio: no se envió.", id);
			return;
		}
		if (comprobante.getEstadoEnvio() != EstadoEnvio.PENDIENTE) {
			return;
		}
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		try {
			Comprobante modificado = comprobante.getModificaId() == null ? null
					: comprobantes.findById(comprobante.getModificaId()).orElse(null);
			ResultadoEnvio resultado = emisor.enviar(DocumentoElectronico.de(comprobante, modificado));
			comprobante.registrarEnvio(resultado, ahora);
		}
		catch (RuntimeException e) {
			LOG.warn("El envío de {} al OSE falló ({}): queda PENDIENTE.", comprobante.numeroCompleto(),
					e.getClass().getSimpleName());
			comprobante.registrarFalloEnvio(SIN_RESPUESTA, ahora);
		}
	}

	@PreDestroy
	void detener() {
		if (ejecutor != null) {
			ejecutor.shutdown();
		}
	}
}
