package pe.edu.virgenmaria.cuentasclaras.seguridad.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.seguridad.config.PropiedadesSesion;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Límite de intentos de ingreso por conexión (sprint 7, tanda 3; H9, A07 de OWASP, decisión 104). En memoria (una sola
 * instancia, como los latidos; sección 17).
 * <ul>
 *   <li>Con un mismo usuario: {@code intentos-por-cuenta-e-ip} (5) fallidos en 15 minutos desde una conexión y esa
 *       conexión espera 15 minutos para ese usuario. Esos intentos NO llegan al contador de la cuenta: un tercero no puede
 *       bloquear la cuenta de la promotora desde su conexión (el bloqueo de la cuenta, {@code intentos-maximos}, queda para
 *       un ataque desde varias conexiones).</li>
 *   <li>Con cualquier usuario: {@code intentos-por-ip} (20) fallidos en 15 minutos y la conexión espera 15 minutos.</li>
 * </ul>
 * Mientras espera, el ingreso responde 429 sin mirar la clave ni la cuenta.
 */
@Component
public class LimiteIngresos {

	/** Por qué espera una conexión. */
	public enum Motivo {
		CONEXION("Demasiados intentos desde esta conexión. Espera %d minutos."),
		CUENTA("Demasiados intentos con este usuario desde esta conexión. Espera %d minutos.");

		private final String mensaje;

		Motivo(String mensaje) {
			this.mensaje = mensaje;
		}

		public String mensaje(long minutos) {
			return mensaje.formatted(minutos);
		}
	}

	/** Si una conexión empezó a esperar con este intento fallido (para dejarlo en la bitácora una sola vez). */
	public record Fallo(Optional<Motivo> empiezaAEsperar) {
	}

	private static final int MAXIMO_REGISTROS = 10_000;

	private final PropiedadesSesion propiedades;

	private final Clock reloj;

	private final Map<String, Deque<Instant>> fallosPorIp = new HashMap<>();

	private final Map<String, Deque<Instant>> fallosPorCuenta = new HashMap<>();

	private final Map<String, Instant> esperaIp = new HashMap<>();

	private final Map<String, Instant> esperaCuenta = new HashMap<>();

	public LimiteIngresos(PropiedadesSesion propiedades, Clock reloj) {
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/** Si esta conexión debe esperar antes de intentar con este usuario. */
	public synchronized Optional<Motivo> espera(String ip, String usuario) {
		Instant ahora = reloj.instant();
		if (vigente(esperaIp.get(clave(ip)), ahora)) {
			return Optional.of(Motivo.CONEXION);
		}
		if (vigente(esperaCuenta.get(clave(ip, usuario)), ahora)) {
			return Optional.of(Motivo.CUENTA);
		}
		return Optional.empty();
	}

	/** Un intento fallido desde esta conexión con este usuario. */
	public synchronized Fallo fallo(String ip, String usuario) {
		Instant ahora = reloj.instant();
		purgar(ahora);
		Optional<Motivo> empieza = Optional.empty();
		if (sumar(fallosPorCuenta, clave(ip, usuario), ahora) >= propiedades.intentosPorCuentaEIp()
				&& !vigente(esperaCuenta.get(clave(ip, usuario)), ahora)) {
			esperaCuenta.put(clave(ip, usuario), ahora.plus(propiedades.ventanaIntentosIp()));
			fallosPorCuenta.remove(clave(ip, usuario));
			empieza = Optional.of(Motivo.CUENTA);
		}
		if (sumar(fallosPorIp, clave(ip), ahora) >= propiedades.intentosPorIp() && !vigente(esperaIp.get(clave(ip)), ahora)) {
			esperaIp.put(clave(ip), ahora.plus(propiedades.ventanaIntentosIp()));
			fallosPorIp.remove(clave(ip));
			empieza = Optional.of(Motivo.CONEXION);
		}
		return new Fallo(empieza);
	}

	/** Minutos que espera una conexión (para el mensaje). */
	public long minutos() {
		return propiedades.ventanaIntentosIp().toMinutes();
	}

	private int sumar(Map<String, Deque<Instant>> fallos, String clave, Instant ahora) {
		Deque<Instant> lista = fallos.computeIfAbsent(clave, k -> new ArrayDeque<>());
		lista.addLast(ahora);
		Instant desde = ahora.minus(propiedades.ventanaIntentosIp());
		while (!lista.isEmpty() && lista.peekFirst().isBefore(desde)) {
			lista.pollFirst();
		}
		return lista.size();
	}

	/** Olvida lo vencido; si aun así hay demasiadas conexiones registradas, empieza de nuevo (memoria acotada). */
	private void purgar(Instant ahora) {
		Instant desde = ahora.minus(propiedades.ventanaIntentosIp());
		esperaIp.values().removeIf(hasta -> !hasta.isAfter(ahora));
		esperaCuenta.values().removeIf(hasta -> !hasta.isAfter(ahora));
		fallosPorIp.values().removeIf(l -> l.isEmpty() || l.peekLast().isBefore(desde));
		fallosPorCuenta.values().removeIf(l -> l.isEmpty() || l.peekLast().isBefore(desde));
		if (fallosPorIp.size() + fallosPorCuenta.size() > MAXIMO_REGISTROS) {
			fallosPorIp.clear();
			fallosPorCuenta.clear();
		}
	}

	private static boolean vigente(Instant hasta, Instant ahora) {
		return hasta != null && hasta.isAfter(ahora);
	}

	private static String clave(String ip) {
		return ip == null ? "?" : ip;
	}

	private static String clave(String ip, String usuario) {
		return clave(ip) + "|" + (usuario == null ? "" : usuario);
	}
}
