package pe.edu.virgenmaria.cuentasclaras.seguridad.web;

import pe.edu.virgenmaria.cuentasclaras.seguridad.config.ModuloApp;

/**
 * Rediseño de la interfaz (hallazgo 3): un número junto a una opción del menú («3 por aprobar»). Cada módulo que ya
 * cuenta algo lo publica con un bean; el menú solo lo pide si el módulo está en el menú de quien está en sesión (el
 * servicio que cuenta exige además su propio rol).
 */
public interface ContadorMenu {

	/** El módulo junto al que va el número. */
	ModuloApp modulo();

	/** Cuántos elementos esperan una acción. */
	long contar();

	/** Texto para lectores de pantalla, por ejemplo «3 por aprobar». */
	String descripcion(long cantidad);

	/** {@code true}: pide una acción (rojo); {@code false}: es un aviso (ámbar). */
	default boolean urgente() {
		return true;
	}
}
