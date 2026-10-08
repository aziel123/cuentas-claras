package pe.edu.virgenmaria.cuentasclaras.comun.config;

import java.time.Duration;

/**
 * Un reloj que se puede mover. SOLO existe en desarrollo ({@link RelojDesarrollo}, perfil {@code dev}) y en pruebas: lo
 * usan los datos de demostración para armar un día anterior (cajas cerradas ayer) con los mismos servicios. En
 * producción no hay ningún bean de este tipo.
 */
public interface RelojMovible {

	/** Mueve el reloj (negativo: hacia atrás). */
	void mover(Duration desplazamiento);
}
