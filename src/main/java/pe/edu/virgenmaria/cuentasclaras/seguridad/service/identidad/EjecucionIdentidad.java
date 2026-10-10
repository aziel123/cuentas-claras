package pe.edu.virgenmaria.cuentasclaras.seguridad.service.identidad;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.RutaConexion;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * La ruta de identidad (sprint 7, tanda 2; secciones 3.2 y 3.3 del diseño): altas, claves, bloqueo, desactivación, roles,
 * contacto del personal y sesiones se escriben con la conexión de {@code cc_sistema}, el único usuario de MySQL con GRANT
 * sobre {@code usuario}, {@code usuario_rol} y {@code sesion_usuario} (H1: con la clave de {@code cc_app} no se crea una
 * cuenta, no se cambia la clave de la directora ni se da el rol PROMOTOR).
 * <ul>
 *   <li>Abre SU PROPIA transacción y <b>falla si ya hay una activa</b>: una operación de identidad nunca se mezcla con una
 *       de negocio en curso (que ya tiene la conexión de {@code cc_app}).</li>
 *   <li>Mantiene el {@code SecurityContext} de la persona: la bitácora registra a quien la hizo, no a un actor de
 *       sistema.</li>
 *   <li>La usan solo las clases autorizadas en {@code ReglasArquitecturaTest} (ingreso, sesiones, gestión de usuarios y
 *       de accesos, cambio de clave, activación, contacto y roles del personal, la bandeja para esos tipos y los
 *       inicializadores).</li>
 * </ul>
 * Si el código se equivoca y escribe identidad fuera de esta ruta, MySQL lo rechaza (1142): falla cerrado.
 */
@Component
public class EjecucionIdentidad {

	private final TransactionTemplate transaccion;

	public EjecucionIdentidad(PlatformTransactionManager transacciones) {
		this.transaccion = new TransactionTemplate(transacciones);
	}

	public <T> T como(Supplier<T> operacion) {
		return como(operacion, List.of());
	}

	public void ejecutar(Runnable operacion) {
		Objects.requireNonNull(operacion, "operacion");
		como(() -> {
			operacion.run();
			return null;
		});
	}

	/**
	 * Igual que {@link #como(Supplier)}, pero si la operación lanza una de {@code sinReversion} la transacción SE CONFIRMA
	 * (por ejemplo, el intento fallido y su bitácora al escribir mal la clave actual) y después se relanza la excepción.
	 */
	public <T> T como(Supplier<T> operacion, List<Class<? extends RuntimeException>> sinReversion) {
		Objects.requireNonNull(operacion, "operacion");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Una operación de identidad abre su propia transacción con la conexión de "
					+ "cc_sistema: no se llama con una transacción abierta");
		}
		RuntimeException[] diferida = new RuntimeException[1];
		T resultado = RutaConexion.identidad(() -> transaccion.execute(estado -> {
			try {
				return operacion.get();
			}
			catch (RuntimeException e) {
				if (sinReversion.stream().anyMatch(tipo -> tipo.isInstance(e))) {
					diferida[0] = e;
					return null;
				}
				throw e;
			}
		}));
		if (diferida[0] != null) {
			throw diferida[0];
		}
		return resultado;
	}
}
