package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Límite de avisos del webhook de WhatsApp por IP y por minuto (G14), en memoria (una sola instancia, como el de la
 * pasarela). Lo que pasa del límite responde 429 sin leer el aviso.
 */
@Component
public class LimiteWebhookPorIp {

	private static final int MAXIMO_IPS = 10_000;

	private final Map<String, Integer> contadores = new HashMap<>();

	private final int maximoPorMinuto;

	private final Clock reloj;

	private long minutoActual = -1;

	public LimiteWebhookPorIp(@Value("${cuentasclaras.mensajeria.avisos-por-minuto-por-ip:120}") int maximoPorMinuto,
			Clock reloj) {
		if (maximoPorMinuto < 1 || maximoPorMinuto > 100_000) {
			throw new IllegalArgumentException("cuentasclaras.mensajeria.avisos-por-minuto-por-ip: de 1 a 100000");
		}
		this.maximoPorMinuto = maximoPorMinuto;
		this.reloj = reloj;
	}

	/** @return {@code true} si esa IP todavía puede enviar un aviso en este minuto (y lo cuenta) */
	public synchronized boolean permitir(String ip) {
		long minuto = reloj.millis() / 60_000;
		if (minuto != minutoActual) {
			contadores.clear();
			minutoActual = minuto;
		}
		String clave = ip == null ? "?" : ip;
		if (!contadores.containsKey(clave) && contadores.size() >= MAXIMO_IPS) {
			return false;
		}
		int cuenta = contadores.merge(clave, 1, Integer::sum);
		return cuenta <= maximoPorMinuto;
	}
}
