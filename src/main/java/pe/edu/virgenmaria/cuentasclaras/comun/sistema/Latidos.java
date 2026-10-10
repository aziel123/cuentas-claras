package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Último éxito de cada proceso programado (sprint 7, monitoreo). Lo alimenta {@link ObservadorLatidos} cuando una tarea
 * {@code @Scheduled} termina sin error; también puede llamarse a mano. Vive en memoria: con una sola instancia de la
 * aplicación basta (riesgo residual del diseño: con dos instancias se rehace). La hora sale del {@link Clock} de la
 * aplicación.
 */
@Component
public class Latidos {

	private final Clock reloj;

	private final Instant arranque;

	private final Map<String, Instant> ultimos = new ConcurrentHashMap<>();

	public Latidos(Clock reloj) {
		this.reloj = reloj;
		this.arranque = reloj.instant();
	}

	/** El proceso {@code proceso} (por ejemplo {@code DespachoMensajes.despachar}) terminó bien ahora. */
	public void latido(String proceso) {
		ultimos.put(proceso, reloj.instant());
	}

	public Map<String, Instant> ultimos() {
		return Map.copyOf(ultimos);
	}

	/** Cuándo arrancó la aplicación: tras un arranque hay unos minutos de gracia antes de dar un proceso por atrasado. */
	public Instant arranque() {
		return arranque;
	}
}
