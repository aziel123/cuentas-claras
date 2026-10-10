package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;

import java.lang.reflect.Method;

/**
 * Registra un latido cuando una tarea {@code @Scheduled} termina SIN error (Spring observa cada ejecución si el registro
 * de tareas tiene un {@code ObservationRegistry}: lo pone {@link ConfiguracionTareas}). Así ningún proceso tiene que
 * acordarse de avisar que corrió, y uno que falla siempre deja de latir.
 */
public class ObservadorLatidos implements ObservationHandler<ScheduledTaskObservationContext> {

	private final Latidos latidos;

	public ObservadorLatidos(Latidos latidos) {
		this.latidos = latidos;
	}

	@Override
	public boolean supportsContext(Observation.Context contexto) {
		return contexto instanceof ScheduledTaskObservationContext;
	}

	@Override
	public void onStart(ScheduledTaskObservationContext contexto) {
		PasadaPorColegios.reiniciar();
	}

	/**
	 * Correcciones del sprint 7 (QA-S7-2): tampoco late si una pasada por los colegios falló en alguno
	 * ({@link PasadaPorColegios}), aunque la tarea haya terminado sin excepción.
	 */
	@Override
	public void onStop(ScheduledTaskObservationContext contexto) {
		boolean fallas = PasadaPorColegios.huboFallas();
		PasadaPorColegios.reiniciar();
		if (contexto.isComplete() && contexto.getError() == null && !fallas) {
			latidos.latido(nombre(contexto.getMethod()));
		}
	}

	/** {@code DespachoMensajes.despachar}: la clase que declara el método (nunca el proxy) y el método. */
	public static String nombre(Method metodo) {
		return metodo.getDeclaringClass().getSimpleName() + "." + metodo.getName();
	}

	/**
	 * La misma clave desde el texto de una tarea programada ({@code ScheduledMethodRunnable.toString()}, que Spring
	 * conserva aunque envuelva la tarea): {@code pe.edu...proceso.DespachoMensajes.despachar} da
	 * {@code DespachoMensajes.despachar}; una clase anidada ({@code Externa$Interna}) da {@code Interna.metodo}.
	 */
	public static String nombre(String tarea) {
		int punto = tarea.lastIndexOf('.');
		if (punto <= 0) {
			return tarea;
		}
		String clase = tarea.substring(0, punto);
		clase = clase.substring(clase.lastIndexOf('.') + 1);
		clase = clase.substring(clase.lastIndexOf('$') + 1);
		return clase + tarea.substring(punto);
	}
}
