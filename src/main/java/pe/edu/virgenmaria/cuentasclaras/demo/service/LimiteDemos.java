package pe.edu.virgenmaria.cuentasclaras.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprint 8, tanda 3 (decisión 126, E23): cuántas demos se crean. Como máximo {@code por-conexion-al-dia} desde una misma
 * conexión (IP) y {@code por-dia} en total, por día de Lima. En memoria: la demo es una sola instancia y se reinicia cada
 * noche (el contador vuelve a cero con ella). Así nadie llena la demo con miles de colegios.
 */
@Component
@Profile("demo")
public class LimiteDemos {

	private final int porConexion;

	private final int porDia;

	private final Clock reloj;

	private LocalDate dia;

	private int hoy;

	private final Map<String, Integer> porIp = new HashMap<>();

	public LimiteDemos(@Value("${cuentasclaras.demo.por-conexion-al-dia:3}") int porConexion,
			@Value("${cuentasclaras.demo.por-dia:50}") int porDia, Clock reloj) {
		this.porConexion = porConexion;
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
		}
		String clave = ip == null ? "?" : ip;
		if (hoy >= porDia) {
			throw new ReglaNegocioException("Hoy ya se crearon todas las demos disponibles (" + porDia + "). Vuelve mañana o "
					+ "escríbenos para que te la mostremos.");
		}
		if (porIp.getOrDefault(clave, 0) >= porConexion) {
			throw new ReglaNegocioException("Desde esta conexión ya creaste " + porConexion + " demos hoy. Usa la que ya "
					+ "tienes (sus claves están en la página que te mostramos) o vuelve mañana.");
		}
		hoy++;
		porIp.merge(clave, 1, Integer::sum);
	}

	/** Si la creación falló, la reserva se devuelve (no cuenta). */
	public synchronized void devolver(String ip) {
		String clave = ip == null ? "?" : ip;
		if (hoy > 0) {
			hoy--;
		}
		porIp.computeIfPresent(clave, (k, v) -> v > 1 ? v - 1 : null);
	}
}
