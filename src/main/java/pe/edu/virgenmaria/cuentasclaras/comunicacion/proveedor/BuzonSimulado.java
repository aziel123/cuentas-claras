package pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.CanalMensaje;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.model.ProveedorMensajeria;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ResultadoEnvio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que «enviaron» los proveedores SIMULADOS (sprint 5): nadie lo recibe. Solo existe en dev, test y piloto (regla
 * ArchUnit) y vive en memoria (los últimos {@value #MAXIMO}). Las pruebas leen aquí el enlace de activación que habría
 * llegado al titular y pueden programar fallas del proveedor. En dev, además, el enlace de activación sale en el log
 * para poder probar el flujo en local (nunca en el piloto ni en prod).
 */
@Component
@Profile({ "dev", "test", "piloto" })
public class BuzonSimulado {

	private static final Logger LOG = LoggerFactory.getLogger(BuzonSimulado.class);

	static final int MAXIMO = 500;

	/** Un mensaje que habría salido. */
	public record Entrega(CanalMensaje canal, String destino, String asunto, String texto, String sufijoBoton,
			String idProveedor) {
	}

	private final Deque<Entrega> entregas = new ArrayDeque<>();

	private final Deque<ResultadoEnvio> programadas = new ArrayDeque<>();

	private final boolean registrarEnlacesEnLog;

	public BuzonSimulado(Environment entorno) {
		this.registrarEnlacesEnLog = Arrays.asList(entorno.getActiveProfiles()).contains("dev");
	}

	/** Recibe un envío: la respuesta programada (si hay) o ACEPTADO con un id simulado. */
	synchronized ResultadoEnvio recibir(CanalMensaje canal, String destino, String asunto, String texto,
			String sufijoBoton) {
		ResultadoEnvio programada = programadas.pollFirst();
		if (programada != null && !programada.aceptado()) {
			return programada;
		}
		String id = "SIM-" + UUID.randomUUID();
		entregas.addLast(new Entrega(canal, destino, asunto, texto, sufijoBoton, id));
		while (entregas.size() > MAXIMO) {
			entregas.pollFirst();
		}
		if (registrarEnlacesEnLog && sufijoBoton != null) {
			LOG.info("[dev] Enlace de activación simulado para {}: {}", destino, sufijoBoton);
		}
		return ResultadoEnvio.aceptado(ProveedorMensajeria.SIMULADO, id);
	}

	/** Las próximas respuestas del proveedor (por ejemplo, errores para probar los reintentos). */
	public synchronized void programar(ResultadoEnvio... respuestas) {
		programadas.addAll(List.of(respuestas));
	}

	public synchronized List<Entrega> entregas() {
		return new ArrayList<>(entregas);
	}

	/** La última entrega a ese destino. */
	public synchronized Optional<Entrega> ultimaPara(String destino) {
		return entregas.reversed().stream().filter(e -> e.destino().equals(destino)).findFirst();
	}

	public synchronized void vaciar() {
		entregas.clear();
		programadas.clear();
	}
}
