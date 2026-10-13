package pe.edu.virgenmaria.cuentasclaras.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sprint 8, tanda 3 (decisión 126, E23): cuántas demos se crean, por día de Lima. En memoria: la demo es una sola instancia
 * y se reinicia cada noche (el contador vuelve a cero con ella). Así nadie llena la demo con miles de colegios.
 * <p>
 * Correcciones del sprint 8 (S8-B3): con 3 por conexión y 50 al día, unas 17 direcciones IP agotaban la demo de todos. Ahora
 * hay tres topes: por conexión ({@code por-conexion-al-dia}, 3), por RED ({@code por-red-al-dia}, 6: la misma /24 de IPv4 o
 * /48 de IPv6, así varias IP de un mismo proveedor cuentan juntas) y el total ({@code por-dia}, 120: lo que la VM de la demo
 * soporta en memoria). Agotar el total exige ahora 20 redes distintas en un día. Residual (documentado): quien tenga más
 * redes (una botnet) puede agotarlo; la demo se reinicia a las 03:00 y el responsable técnico la puede reiniciar antes, y
 * Caddy limita las peticiones por IP.
 */
@Component
@Profile("demo")
public class LimiteDemos {

	private final int porConexion;

	private final int porRed;

	private final int porDia;

	private final Clock reloj;

	private LocalDate dia;

	private int hoy;

	private final Map<String, Integer> porIp = new HashMap<>();

	private final Map<String, Integer> porRedes = new HashMap<>();

	public LimiteDemos(@Value("${cuentasclaras.demo.por-conexion-al-dia:3}") int porConexion,
			@Value("${cuentasclaras.demo.por-red-al-dia:6}") int porRed,
			@Value("${cuentasclaras.demo.por-dia:120}") int porDia, Clock reloj) {
		this.porConexion = porConexion;
		this.porRed = porRed;
		this.porDia = porDia;
		this.reloj = reloj;
	}

	/** Reserva una demo para esa conexión, o explica por qué no se puede. */
	public synchronized void reservar(String ip) {
		LocalDate fecha = LocalDate.now(reloj.withZone(ConfiguracionTiempo.ZONA_LIMA));
		if (!fecha.equals(dia)) {
			dia = fecha;
			hoy = 0;
			porIp.clear();
			porRedes.clear();
		}
		String clave = ip == null ? "?" : ip;
		String red = red(clave);
		if (hoy >= porDia) {
			throw new ReglaNegocioException("Hoy ya se crearon todas las demos disponibles (" + porDia + "). Vuelve mañana o "
					+ "escríbenos para que te la mostremos.");
		}
		if (porIp.getOrDefault(clave, 0) >= porConexion) {
			throw new ReglaNegocioException("Desde esta conexión ya creaste " + porConexion + " demos hoy. Usa la que ya "
					+ "tienes (sus claves están en la página que te mostramos) o vuelve mañana.");
		}
		if (porRedes.getOrDefault(red, 0) >= porRed) {
			throw new ReglaNegocioException("Desde tu red ya se crearon " + porRed + " demos hoy. Usa una de las que ya "
					+ "tienen o vuelve mañana.");
		}
		hoy++;
		porIp.merge(clave, 1, Integer::sum);
		porRedes.merge(red, 1, Integer::sum);
	}

	/** Si la creación falló, la reserva se devuelve (no cuenta). */
	public synchronized void devolver(String ip) {
		String clave = ip == null ? "?" : ip;
		if (hoy > 0) {
			hoy--;
		}
		porIp.computeIfPresent(clave, (k, v) -> v > 1 ? v - 1 : null);
		porRedes.computeIfPresent(red(clave), (k, v) -> v > 1 ? v - 1 : null);
	}

	/** La red de la conexión: los 3 primeros octetos de una IPv4 (/24) o los 3 primeros grupos de una IPv6 (/48). */
	static String red(String ip) {
		if (ip == null) {
			return "?";
		}
		String texto = ip.strip().toLowerCase(Locale.ROOT);
		if (texto.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
			return texto.substring(0, texto.lastIndexOf('.'));
		}
		if (texto.contains(":")) {
			String[] grupos = texto.split(":", -1);
			return grupos.length >= 3 ? grupos[0] + ":" + grupos[1] + ":" + grupos[2] : texto;
		}
		return texto;
	}
}
