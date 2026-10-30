package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

/**
 * Las bases H2 de la demostración local (perfil {@code dev}): en memoria ({@code jdbc:h2:mem:}, se pierde al reiniciar) o
 * en un archivo ({@code jdbc:h2:file:}, perfil adicional {@code persistente}: la demo sobrevive al reinicio). Los
 * cargadores de datos de demostración solo escriben en una de estas: nunca en MySQL.
 */
public final class BaseH2Local {

	public static final String PREFIJO_MEMORIA = "jdbc:h2:mem:";

	public static final String PREFIJO_ARCHIVO = "jdbc:h2:file:";

	private BaseH2Local() {
	}

	/** Si la URL es de una base H2 local (en memoria o en archivo). */
	public static boolean es(String url) {
		return url != null && (url.startsWith(PREFIJO_MEMORIA) || url.startsWith(PREFIJO_ARCHIVO));
	}
}
