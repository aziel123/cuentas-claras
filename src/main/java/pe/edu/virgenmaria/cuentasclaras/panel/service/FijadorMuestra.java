package pe.edu.virgenmaria.cuentasclaras.panel.service;

import pe.edu.virgenmaria.cuentasclaras.panel.model.MuestraLlamada;

import java.time.LocalDate;
import java.util.List;

/**
 * Pide a {@code sistema.panel} que fije la muestra de la semana si aún no existe (sprint 7, tanda 2; sección 3.6). Lo
 * implementa {@code panel.proceso.MuestraSemanal} (en una transacción PROPIA, con la conexión de {@code cc_sistema}):
 * la pantalla de una persona nunca inserta la muestra.
 */
public interface FijadorMuestra {

	/** La muestra de esa semana del colegio, fijada ahora si todavía no lo estaba. */
	List<MuestraLlamada> asegurar(long colegioId, LocalDate semana);
}
