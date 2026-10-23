package pe.edu.virgenmaria.cuentasclaras.comun.sistema;

/**
 * Rediseño (datos con historia, solo perfil {@code dev}): una tarea programada que el historial de demostración pide
 * correr al final de cada día simulado (enviar las boletas al OSE simulado, enviar los avisos a las familias). Así cada
 * módulo expone la suya sin que la caja dependa de él.
 */
public interface TareasDelDiaDev {

	/** Corre la tarea en el colegio hasta dejar su cola vacía. */
	void pasar(long colegioId);
}
