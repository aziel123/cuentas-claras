package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.PrincipalSistema;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Con qué usuario de MySQL se abre la conexión (sprint 7, tanda 2; sección 3.2 del diseño). La decide
 * {@link FuenteDatosEnrutada} al pedir la conexión, es decir, al empezar la transacción:
 * <ul>
 *   <li>{@link #SISTEMA} ({@code cc_sistema}) si quien trabaja es un actor de sistema ({@link PrincipalSistema} en el
 *       {@code SecurityContext}) o si el hilo está en la ruta de identidad ({@link #identidad});</li>
 *   <li>{@link #APP} ({@code cc_app}) en cualquier otro caso, también sin contexto (arranque, validación de Hibernate):
 *       el valor por defecto es el de menos permisos.</li>
 * </ul>
 * Si el código se equivoca de ruta, el sistema falla cerrado: el trigger rechaza la fila {@code sistema.*} escrita por
 * {@code cc_app} (1644) y el GRANT rechaza la escritura de identidad (1142).
 */
public enum RutaConexion {

	/** Las peticiones de las personas ({@code cc_app}). */
	APP,

	/** Los procesos {@code sistema.*} y la identidad: ingreso, sesiones, claves, roles y altas ({@code cc_sistema}). */
	SISTEMA;

	private static final ThreadLocal<Integer> IDENTIDAD = new ThreadLocal<>();

	/** La ruta de este hilo, ahora. */
	public static RutaConexion actual() {
		Integer identidad = IDENTIDAD.get();
		if (identidad != null && identidad > 0) {
			return SISTEMA;
		}
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof PrincipalSistema) {
			return SISTEMA;
		}
		return APP;
	}

	/** Si el hilo está dentro de {@link #identidad}. */
	public static boolean enIdentidad() {
		Integer identidad = IDENTIDAD.get();
		return identidad != null && identidad > 0;
	}

	/**
	 * Ejecuta la operación en la ruta de identidad ({@code cc_sistema}) sin cambiar el {@code SecurityContext}: la bitácora
	 * registra a la persona que la hizo. Solo lo usa {@code seguridad.service.identidad.EjecucionIdentidad} (regla
	 * ArchUnit), que además abre su propia transacción.
	 */
	public static <T> T identidad(Supplier<T> operacion) {
		Objects.requireNonNull(operacion, "operacion");
		Integer anterior = IDENTIDAD.get();
		IDENTIDAD.set(anterior == null ? 1 : anterior + 1);
		try {
			return operacion.get();
		}
		finally {
			if (anterior == null) {
				IDENTIDAD.remove();
			}
			else {
				IDENTIDAD.set(anterior);
			}
		}
	}
}
