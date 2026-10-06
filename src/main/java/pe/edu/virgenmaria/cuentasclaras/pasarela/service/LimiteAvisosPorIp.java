package pe.edu.virgenmaria.cuentasclaras.pasarela.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Correcciones del sprint 4 (S4-B4): límite de avisos de la pasarela por IP (por defecto 60 por minuto), para que nadie
 * inunde el webhook ni pruebe colegios en masa. Vive en memoria (una sola instancia: riesgo aceptado en la sección 16
 * del diseño). Lo que pasa del límite responde 429 sin leer el aviso.
 */
@Component
public class LimiteAvisosPorIp {

	private static final Duration VENTANA = Duration.ofMinutes(1);

	/** Cuántas IP distintas se recuerdan como máximo (si se llena, se olvidan las más viejas). */
	private static final int MAXIMO_IPS = 10_000;

	private final Map<String, Deque<Instant>> porIp = new HashMap<>();

	private final int maximoPorMinuto;

	private final Clock reloj;

	public LimiteAvisosPorIp(@Value("${cuentasclaras.pasarela.avisos-por-minuto-por-ip:60}") int maximoPorMinuto,
			Clock reloj) {
		if (maximoPorMinuto < 1 || maximoPorMinuto > 10_000) {
			throw new IllegalArgumentException("cuentasclaras.pasarela.avisos-por-minuto-por-ip debe estar entre 1 y 10000");
		}
		this.maximoPorMinuto = maximoPorMinuto;
		this.reloj = reloj;
	}

	/** @return {@code true} si esa IP todavía puede enviar un aviso en este minuto (y lo cuenta) */
	public synchronized boolean permitir(String ip) {
		Instant ahora = reloj.instant();
		Instant desde = ahora.minus(VENTANA);
		if (porIp.size() >= MAXIMO_IPS) {
			Iterator<Map.Entry<String, Deque<Instant>>> it = porIp.entrySet().iterator();
			while (it.hasNext()) {
				Deque<Instant> marcas = it.next().getValue();
				while (!marcas.isEmpty() && marcas.peekFirst().isBefore(desde)) {
					marcas.removeFirst();
				}
				if (marcas.isEmpty()) {
					it.remove();
				}
			}
			if (porIp.size() >= MAXIMO_IPS) {
				return false;
			}
		}
		Deque<Instant> marcas = porIp.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
		while (!marcas.isEmpty() && marcas.peekFirst().isBefore(desde)) {
			marcas.removeFirst();
		}
		if (marcas.size() >= maximoPorMinuto) {
			return false;
		}
		marcas.addLast(ahora);
		return true;
	}
}
