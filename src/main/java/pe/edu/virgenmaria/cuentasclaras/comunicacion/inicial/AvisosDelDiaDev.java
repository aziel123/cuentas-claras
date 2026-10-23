package pe.edu.virgenmaria.cuentasclaras.comunicacion.inicial;

import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.TareasDelDiaDev;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;

/**
 * Rediseño (datos con historia, solo {@code dev}): los avisos de cada día simulado del historial salen ese mismo día,
 * como lo haría la tarea programada (mensajería SIMULADA en desarrollo: nadie los recibe).
 */
@Component
@Profile("dev")
@Order(2)
public class AvisosDelDiaDev implements TareasDelDiaDev {

	private final DespachoMensajes despacho;

	public AvisosDelDiaDev(DespachoMensajes despacho) {
		this.despacho = despacho;
	}

	@Override
	public void pasar(long colegioId) {
		while (despacho.despacharColegio(colegioId) > 0) {
			// Hasta vaciar la cola de ese día.
		}
	}
}
