package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Cuenta en memoria los avisos de pasarela NO auténticos de la última hora. No se escriben en la bitácora (así nadie
 * puede llenar la cadena HMAC enviando avisos falsos); si pasan del límite, Promotoría ve una alerta de ATENCIÓN.
 */
@Component
public class ContadorAvisosNoAutenticos {

	private static final Duration VENTANA = Duration.ofHours(1);

	private static final int MAXIMO_GUARDADO = 10_000;

	private final Deque<Instant> recibidos = new ArrayDeque<>();

	private final Clock reloj;

	public ContadorAvisosNoAutenticos(Clock reloj) {
		this.reloj = reloj;
	}

	public synchronized void registrar() {
		limpiar();
		if (recibidos.size() < MAXIMO_GUARDADO) {
			recibidos.addLast(reloj.instant());
		}
	}

	public synchronized int ultimaHora() {
		limpiar();
		return recibidos.size();
	}

	private void limpiar() {
		Instant desde = reloj.instant().minus(VENTANA);
		while (!recibidos.isEmpty() && recibidos.peekFirst().isBefore(desde)) {
			recibidos.removeFirst();
		}
	}
}
