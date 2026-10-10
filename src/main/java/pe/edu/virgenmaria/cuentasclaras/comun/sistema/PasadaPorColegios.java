package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

import org.slf4j.Logger;

import java.util.List;
import java.util.function.Consumer;

/**
 * Una pasada de una tarea programada por los colegios activos (correcciones del sprint 7, QA-S7-2). La falla de un colegio
 * se registra en el log (ERROR) y NO corta a los demás, pero la pasada queda marcada como fallida en el hilo de la tarea:
 * {@link ObservadorLatidos} no registra su latido y, si sigue fallando, sale la alerta técnica «Proceso atrasado»
 * (CRÍTICA en el despacho, la huella y el resumen). Antes, cada proceso atrapaba la excepción de cada colegio y la tarea
 * seguía latiendo aunque fallara en todos (por ejemplo, si cc_sistema perdía un GRANT).
 * <p>
 * La marca vive en el hilo de la tarea: el observador la limpia al empezar cada ejecución y la lee al terminar. No se
 * lanza una excepción: las pruebas y los procesos que llaman a la tarea directamente siguen igual.
 */
public final class PasadaPorColegios {

	private static final ThreadLocal<int[]> FALLAS = ThreadLocal.withInitial(() -> new int[1]);

	private PasadaPorColegios() {
	}

	/**
	 * Corre {@code tarea} en cada colegio, en orden. Si alguno falla, lo registra y marca la pasada como fallida (sin
	 * latido).
	 *
	 * @param proceso qué hace la tarea, para el log (sin datos personales)
	 * @return cuántos colegios fallaron
	 */
	public static int recorrer(Logger log, String proceso, List<Long> colegios, Consumer<Long> tarea) {
		int fallas = 0;
		for (Long colegio : colegios) {
			try {
				tarea.accept(colegio);
			}
			catch (RuntimeException e) {
				fallas++;
				log.error("{} falló en el colegio {}: {}", proceso, colegio, e.getClass().getSimpleName());
			}
		}
		if (fallas > 0) {
			FALLAS.get()[0] += fallas;
			log.error("{} falló en {} de {} colegio(s): la tarea no deja latido.", proceso, fallas, colegios.size());
		}
		return fallas;
	}

	/** Al empezar una ejecución de una tarea programada: sin fallas. */
	static void reiniciar() {
		FALLAS.remove();
	}

	/** Si alguna pasada de la ejecución en curso falló en algún colegio. */
	static boolean huboFallas() {
		return FALLAS.get()[0] > 0;
	}
}
